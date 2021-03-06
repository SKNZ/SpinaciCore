package wow.auth.data

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import scalikejdbc._
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import wow.api.API
import wow.auth.crypto.{Srp6Identity, Srp6Protocol}
import wow.common.database.{Databases, RichColumn, databasecomponent}
import wow.utils.BigIntExtensions._

/**
  * Account information
  */
final case class Account(
  id: Int,
  login: String,
  @databasecomponent
  var identity: Srp6Identity,
  var sessionKey: Option[BigInt] = None)

object Account extends SQLSyntaxSupport[Account] with RichColumn[Account] {
  override def connectionPoolName: Any = Databases.AuthServer

  def apply(s: SyntaxProvider[Account])(rs: WrappedResultSet): Account = apply(s.resultName)(rs)

  def apply(rn: ResultName[Account])(rs: WrappedResultSet): Account = {
    val id = rs.int(c.id)
    val login = rs.string(c.login)

    val salt = rs.bigInt(c.salt)
    val verifier = rs.bigInt(c.verifier)
    val identity = Srp6Identity(salt, verifier)

    val sessionKey = rs.bigIntOpt(c.sessionKey)

    Account(id, login, identity, sessionKey)
  }

  def create(login: String, identity: Srp6Identity)(implicit session: DBSession = autoSession): Int = withSQL {
    insert.into(Account)
      .namedValues(
        c.login -> login.toUpperCase(),
        c.salt -> identity.salt,
        c.verifier -> identity.verifier
      )
  }.updateAndReturnGeneratedKey.apply().toInt

  def save(account: Account)(implicit session: DBSession = autoSession): Int = withSQL {
    update(Account)
      .set(
        c.salt -> account.identity.salt,
        c.verifier -> account.identity.verifier,
        c.sessionKey -> account.sessionKey
      )
      .where
      .eq(c.id, account.id)
  }.update.apply()

  def find(condition: SQLSyntax)(implicit session: DBSession = autoSession): Option[Account] = withSQL {
    select(column.*)
      .from(Account as syntax)
      .where
      .append(condition)
  }.map(Account(syntax)).single.apply()

  def findByLogin(login: String)(implicit session: DBSession = autoSession): Option[Account] =
    find(sqls.eq(c.login, login))

  def findById(id: Int)(implicit session: DBSession = autoSession): Option[Account] =
    find(sqls.eq(c.id, id))
}

object AccountAPI extends API {

  case class AccountReq(login: String, password: String)

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  implicit val userFormat: RootJsonFormat[AccountReq] = jsonFormat2(AccountReq.apply)

  override val route: Route =
    pathPrefix("account") {
      get {
        path("online") {
          pathEnd {
            // TODO: Evalute online users
            complete(StatusCodes.OK)
          }
        }
      } ~
        post {
          path("create") {
            pathEnd {
              entity(as[AccountReq]) { accountReq =>
                // TODO: Check if already existing account
                // It must not exist
                val srp = new Srp6Protocol()
                val identity = srp.computeSaltAndVerifier(accountReq.login, accountReq.password)

                Account.create(accountReq.login, identity)
                complete(StatusCodes.Created)
              }
            }
          } ~
            path("delete") {
              pathEnd {
                entity(as[String]) { _ =>
                  // TODO: Delete account
                  // It must exist
                  // Account.deleteAccount(user.username, user.password)
                  complete(StatusCodes.NotImplemented)
                }
              }
            }
        } ~
        put {
          path("reinitialize") {
            pathEnd {
              entity(as[AccountReq]) { accountReq =>
                Account.findByLogin(accountReq.login) match {
                  case Some(account: Account) =>
                    val srp = new Srp6Protocol()
                    val identity: Srp6Identity = srp.computeSaltAndVerifier(accountReq.login, accountReq.password)
                    account.identity = identity

                    Account.save(account)
                    complete(StatusCodes.OK)
                  case None =>
                    complete(StatusCodes.BadRequest)
                }
              }
            }
          }
        }
    }
}


