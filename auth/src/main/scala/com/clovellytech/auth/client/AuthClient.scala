package com.clovellytech.auth
package client

import cats.data.OptionT
import cats.implicits._
import cats.effect.Sync
import com.clovellytech.auth.infrastructure.endpoint.{AuthEndpoints, UserRequest}
import com.clovellytech.auth.infrastructure.repository.persistent.{TokenRepositoryInterpreter, UserRepositoryInterpreter}
import org.http4s._
import org.http4s.dsl._
import org.http4s.client.dsl._
import tsec.passwordhashers.jca.BCrypt
import domain.tokens.TokenService
import domain.users.UserService
import doobie.util.transactor.Transactor


class AuthClient[F[_]: Sync](userService: UserService[F], tokenService: TokenService[F]) extends Http4sDsl[F] with Http4sClientDsl[F] {
  val authEndpoints: AuthEndpoints[F, BCrypt] = new AuthEndpoints[F, BCrypt](userService, tokenService, BCrypt)

  def injectAuthHeader(from: Response[F])(to: Request[F]): Request[F] =
    to.withHeaders(from.headers.filter(_.name.toString == "Authorization"))

  def lookupOrThrow(req: F[Request[F]]): F[Response[F]] = req.flatMap(authEndpoints.endpoints.orNotFound run _)

  def threadResponse(resp: Response[F])(req: Request[F]): F[Response[F]] = {
    val sessionReq = req.withHeaders(resp.headers.filter(_.name.toString.startsWith("Authorization")))
    authEndpoints.endpoints.orNotFound run sessionReq
  }

  def deleteUser(username: String): F[Unit] = (for {
    u <- userService.byUsername(username)
    (_, uid, _) = u
    _ <- OptionT.liftF(userService.delete(uid))
  } yield ()).getOrElse(())

  def postUser(userRequest: UserRequest): F[Response[F]] = lookupOrThrow(POST(uri("/user"), userRequest))

  def loginUser(userRequest: UserRequest): F[Response[F]] = lookupOrThrow(POST(uri("/login"), userRequest))


  // For the client, simply thread the most recent response back into any request that needs
  // authorization. There should probably be a better way to do this, maybe state monad or something.
  def getUser(userName: String, continue: Response[F]): Either[ParseFailure, F[Response[F]]] =
    Uri.fromString(s"/user/$userName").map(uri => GET(uri).flatMap(threadResponse(continue)(_)))


}

object AuthClient {
  def fromTransactor[F[_] : Sync](xa : Transactor[F]) : AuthClient[F] = {
    val userInterpreter = new UserRepositoryInterpreter[F](xa)
    val userService = new UserService[F](userInterpreter)
    val tokenInterpreter = new TokenRepositoryInterpreter[F](xa)
    val tokenService = new TokenService[F](tokenInterpreter)
    new AuthClient[F](userService, tokenService)
  }
}
