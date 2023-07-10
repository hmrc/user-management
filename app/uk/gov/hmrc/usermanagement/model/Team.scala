package uk.gov.hmrc.usermanagement.model

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{OFormat, __}

case class Team(
   members          : Seq[TeamMember],
   team             : String,
   description      : Option[String],
   documentation    : Option[String],
   slack            : Option[String],
   slackNotification: Option[String]
)

object Team {
  implicit val tmf = TeamMember.format

  val format: OFormat[Team] = {
    ((__ \ "members"            ).format[Seq[TeamMember]]
      ~ (__ \"team"             ).format[String]
      ~ (__ \"description"      ).formatNullable[String]
      ~ (__ \"documentation"    ).formatNullable[String]
      ~ (__ \"slack"            ).formatNullable[String]
      ~ (__ \"slackNotification").formatNullable[String]
      )(Team.apply, unlift(Team.unapply))
  }
}

case class TeamMember(
 username: String,
 role    : String
)

object TeamMember {
  val format: OFormat[TeamMember] = {
    ((__ \ "username").format[String]
      ~ (__ \"role"  ).format[String]
      )(TeamMember.apply, unlift(TeamMember.unapply))
  }
}
