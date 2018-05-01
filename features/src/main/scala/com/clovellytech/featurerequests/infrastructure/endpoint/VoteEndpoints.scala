package com.clovellytech.featurerequests
package infrastructure
package endpoint

import cats.effect.Sync
import cats.implicits._
import org.http4s.dsl.Http4sDsl
import com.clovellytech.auth.BearerAuthService
import com.clovellytech.featurerequests.infrastructure.repository.persistent.VoteRepositoryInterpreter
import db.domain.Vote
import domain.votes.{VoteRequest, VoteService}
import doobie.util.transactor.Transactor
import tsec.authentication._

class VoteEndpoints[F[_]: Sync](voteService: VoteService[F]) extends Http4sDsl[F] {

  def submitVote : BearerAuthService[F] = BearerAuthService {
    case req @ POST -> Root / "vote" asAuthed _ => for {
      voteReq <- req.request.as[VoteRequest]
      vote = Vote(voteReq.featureRequest, req.authenticator.identity.some, voteReq.vote, voteReq.comment)
      _ <- voteService.makeVote(vote)
      resp <- Ok()
    } yield {
      resp
    }
  }

  def endpoints : BearerAuthService[F] = submitVote
}

object VoteEndpoints {
  def persistingEndpoints[F[_]: Sync](xa: Transactor[F]) : VoteEndpoints[F] = {
    val voteRepo =  new VoteRepositoryInterpreter(xa)
    val voteService =  new VoteService(voteRepo)
    new VoteEndpoints(voteService)
  }
}
