package uk.gov.hmrc.usermanagement.model

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{OFormat, __}

case class User(
  displayName  : Option[String],
  familyName   : String,
  givenName    : String,
  organisation : Option[String],
  primaryEmail : String,
  username     : String,
  github       : Option[String],
  phoneNumber  : Option[String],
  role         : Option[Seq[UserRole]]
)

object User {
  implicit val urf = UserRole.format

  val format: OFormat[User] = {
    (( __ \ "displayName"   ).formatNullable[String]
     ~ ( __ \ "familyName"  ).format[String]
     ~ ( __ \ "givenName"   ).format[String]
     ~ ( __ \ "organisation").formatNullable[String]
     ~ ( __ \ "primaryEmail").format[String]
     ~ ( __ \ "username"    ).format[String]
     ~ ( __ \ "github"      ).formatNullable[String]
     ~ ( __ \ "phoneNumber" ).formatNullable[String]
     ~ ( __ \ "role"        ).formatNullable[Seq[UserRole]]
    )(User.apply, unlift(User.unapply))
  }
}

case class UserRole(
  teamName: String,
  role    : String
)

object UserRole {
  val format: OFormat[UserRole] = {
    ((__ \ "teamName").format[String]
      ~  (__ \ "role").format[String]
    )(UserRole.apply, unlift(UserRole.unapply))
  }
}

