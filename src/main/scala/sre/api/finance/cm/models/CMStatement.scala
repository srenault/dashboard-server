package sre.api
package finance
package cm

import java.time.LocalDate
import cats.effect._
import org.http4s.EntityEncoder
import org.http4s.circe._
import io.circe._
import io.circe.literal._

case class CMStatement(
  fitid: String,
  accountId: String,
  date: LocalDate,
  amount: Float,
  label: String,
  balance: Float,
  accurateBalance: Boolean
) {

  def id: String = {
    val value = List(fitid, accountId, date.toString, amount.toString, label)
    CMStatement.computeHash(value)
  }

  def hash: String = {
    val roundBalance = CMStatement.round(balance)
    val value = List(fitid, accountId, date.toString, amount.toString, label, roundBalance.toString)
    CMStatement.computeHash(value)
  }

  override def equals(that: Any): Boolean =
    that match {
      case that: CMStatement =>
        this.hash == that.hash

      case _ => false
    }

  override def hashCode: Int = this.hash.hashCode
}

object CMStatement {
  implicit def entityEncoder[F[_]: Effect]: EntityEncoder[F, CMStatement] = jsonEncoderOf[F, CMStatement]
  implicit val encoder: Encoder[CMStatement] = new Encoder[CMStatement] {
    final def apply(statement: CMStatement): Json = {
      json"""
       {
         "id": ${statement.id},
         "fitid": ${statement.fitid},
         "accountId": ${statement.accountId},
         "date": ${statement.date},
         "amount": ${statement.amount},
         "label": ${statement.label},
         "balance": ${statement.balance},
         "accurateBalance": ${statement.accurateBalance}
       }
      """
    }
  }
  implicit def entitiesEncoder[F[_]: Effect]: EntityEncoder[F, List[CMStatement]] = jsonEncoderOf[F, List[CMStatement]]

  lazy val ORDER_ASC: scala.math.Ordering[CMStatement] =
    scala.math.Ordering.by[CMStatement, Long](_.date.toEpochDay)

  lazy val ORDER_DESC: scala.math.Ordering[CMStatement] =
    ORDER_ASC.reverse

  def computeHash(value: List[String]): String = {
    val s = value.mkString("#")
    java.util.Base64.getEncoder().encodeToString(s.getBytes("UTF-8"))
  }

  def round(n: Double): Float = BigDecimal(2).setScale(0, BigDecimal.RoundingMode.HALF_UP).toFloat

  def merge(statements: List[CMStatement]): List[CMStatement] = {
    statements.groupBy(_.id).map {
      case (_, statementsById) =>
        if (statementsById.size > 1) {
          val (accurateStatements, nonAccurateStatements) = statementsById.distinct.partition(_.accurateBalance)

          if (accurateStatements.size > 1) {
            sys.error(s"""
              |Found more that one accurate statements:
              |${accurateStatements.map(_.toString).mkString("\n")}
            """.stripMargin)
          }

          if (accurateStatements.size == 0 && nonAccurateStatements.size == 1) {
            nonAccurateStatements.head
          } else {
            accurateStatements.headOption.getOrElse {
              sys.error(s"""
                |Unable to find accurateStatements:
                |${nonAccurateStatements.map(_.toString.mkString("\n"))}
                |""".stripMargin)
            }
          }
        } else {
          statementsById.head
        }
    }.toList
  }
}
