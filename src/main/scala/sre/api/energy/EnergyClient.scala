package sre.api
package energy

import cats.effect._
import domoticz._
import electricity.ElectricityClient

case class EnergyClient[F[_]: Effect](electricity: ElectricityClient[F])

object EnergyClient {

  def apply[F[_]: Effect](domoticzClient: DomoticzClient[F], settings: Settings): EnergyClient[F] = {
    val electricity = ElectricityClient(domoticzClient, settings)
    EnergyClient(electricity)
  }
}
