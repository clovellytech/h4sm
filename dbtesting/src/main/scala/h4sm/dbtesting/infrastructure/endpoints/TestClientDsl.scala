package h4sm.dbtesting.infrastructure.endpoints

import cats.Applicative
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.client.dsl.Http4sClientDsl

trait TestClientDsl[F[_]] { this: Http4sDsl[F] with Http4sClientDsl[F] =>
  def post[A : EntityEncoder[F, ?]](a : A, u : Uri)(implicit h : Headers, F: Applicative[F]) = POST(a, u, h.toList : _*)
}