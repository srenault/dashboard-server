package sre.api.finance
package cm

import cats.effect._
import org.http4s.EntityEncoder
import org.http4s.circe._
import io.circe.Encoder
import io.circe.generic.semiauto._
import sre.api.finance.models.Period

case class CMAccountsOverview(
  period: Period,
  accounts: List[CMAccountOverview]
)

object CMAccountsOverview {

  implicit val encoder: Encoder[CMAccountsOverview] =
    deriveEncoder[CMAccountsOverview]

  implicit def entityEncoder[F[_]: Effect]: EntityEncoder[F, CMAccountsOverview] =
    jsonEncoderOf[F, CMAccountsOverview]
}
