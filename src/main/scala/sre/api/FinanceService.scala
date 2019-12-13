package sre.api

import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.circe._
import io.circe.literal._
import io.circe.syntax._
import finance.icompta.IComptaClient
import finance.cm.CMClient
import finance.analytics.AnalyticsClient

case class FinanceService[F[_]: ConcurrentEffect : Timer : ContextShift](
  icomptaClient: IComptaClient[F],
  cmClient: CMClient[F],
  dbClient: DBClient[F],
  settings: Settings
) extends FinanceServiceDsl[F] {

  lazy val analyticsClient = AnalyticsClient(icomptaClient, dbClient, settings)

  val service: HttpService[F] = CorsMiddleware {
    HttpService[F] {

      case GET -> Root / "otp" / transactionId / "status" =>
        cmClient.checkOtpStatus(transactionId).flatMap { otpStatus =>
          Ok(otpStatus)
        }

      case GET -> Root / "accounts" =>
        WithAccountsOverview() { accountsOverview =>
          Ok(accountsOverview.asJson)
        }

      case GET -> Root / "accounts" / AccountIdVar(accountId) :? PeriodDateQueryParamMatcher(maybeValidatedPeriod) =>
        WithPeriodDate(maybeValidatedPeriod) { maybePeriodDate =>
          WithAccountState(accountId, maybePeriodDate) { (period, accountState) =>
            analyticsClient.computeExpensesByCategory(accountState).value.flatMap { expenses =>
              Ok(json"""{ "expenses": $expenses, "period": $period , "account": $accountState }""")
            }
          }
        }

      case GET -> Root / "analytics" =>
        analyticsClient.getPreviousPeriods().flatMap { periods =>
          Ok(json"""{ "result": $periods }""")
        }

      case GET -> Root / "analytics" / "reindex" =>
        analyticsClient.reindex().flatMap(_ => Ok())

      case GET -> Root / "analytics" / "accounts" / accountId / "import" =>
        handleOtpRequest(cmClient.fetchOfxTransactions(accountId)) { response =>
          val accountPath = settings.finance.transactionsDir.toPath.resolve(accountId)
          finance.ofx.OfxStmTrn.persist(is = response.body, accountPath) *> Ok()
        }
    }
  }
}
