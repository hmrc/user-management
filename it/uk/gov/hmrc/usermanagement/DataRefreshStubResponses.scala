package uk.gov.hmrc.usermanagement

object DataRefreshStubResponses {

  val users =
    s"""
       |{
       |  "users": [
       |    {
       |      "displayName": "Joe Bloggs",
       |      "familyName": "Bloggs",
       |      "givenName": "Joseph",
       |      "primaryEmail": "joe.bloggs@gmail.com",
       |      "username": "joe.bloggs",
       |    },
       |    {
       |      "displayName": "New User",
       |      "familyName": "User",
       |      "givenName": "New",
       |      "primaryEmail": "new.user@gmail.com",
       |      "username": "new.user",
       |    },
       |    {
       |      "displayName": "robot",
       |      "familyName": "Doe",
       |      "givenName": "Jane",
       |      "primaryEmail": "test@gmail.com",
       |      "username": "service.platops"
       |    }
       |  ]
       |}
       |""".stripMargin

  val teams =
    s"""
       |{
       |  "teams": [
       |    {
       |      "members": [],
       |      "team": "team1",
       |      "slack": "https://slackchannel.com",
       |      "description": "A great team",
       |      "documentation": "Confluence"
       |    },
       |    {
       |      "team": "team3",
       |      "members": []
       |    }
       |  ]
       |}
       |""".stripMargin

  val team1 =
    s"""
       |{
       |  "members": [
       |    {
       |      "displayName": "Joe Bloggs",
       |      "familyName": "Bloggs",
       |      "givenName": "Joseph",
       |      "primaryEmail": "joe.bloggs@gmail.com",
       |      "username": "joe.bloggs",
       |      "role": "team_admin"
       |    }
       |  ],
       |  "slack": "https://slack.com",
       |  "team": "team1"
       |}
       |""".stripMargin

  val team3 =
    s"""
       |{
       |  "members": [
       |    {
       |      "displayName": "New User",
       |      "familyName": "User",
       |      "givenName": "New",
       |      "primaryEmail": "new.user@gmail.com",
       |      "username": "new.user",
       |      "role": "user"
       |    }
       |  ],
       |  "slack": "https://slack.com",
       |  "team": "team3"
       |}
       |""".stripMargin

}
