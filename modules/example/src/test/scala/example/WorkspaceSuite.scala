package example

import difflicious.Differ
import difflicious.scalatest.ScalatestDiff._
import org.scalatest.funsuite.AnyFunSuite

final class WorkspaceSuite extends AnyFunSuite {
  import WorkspaceSuite._

  test("workspace snapshot matches the provisioned state") {
    Differ[WorkspaceSnapshot].assertNoDiff(
      obtained = WorkspaceSnapshot(
        workspace = Workspace(
          id = "workspace-42",
          name = "payments-platform",
          owner = Person(
            id = "user-7",
            profile = Profile(displayName = "Ada Lovelace", email = "ada@old.example.com"),
          ),
          plan = Plan(name = "team", seatLimit = 20),
        ),
        members = List(
          Member(
            user = Person(
              id = "user-7",
              profile = Profile(displayName = "Ada Lovelace", email = "ada@example.com"),
            ),
            roles = List("admin", "developer"),
            teams = Map(
              "checkout" -> TeamMembership(access = "maintainer", onCall = true),
              "risk" -> TeamMembership(access = "viewer", onCall = false),
            ),
          ),
          Member(
            user = Person(
              id = "user-11",
              profile = Profile(displayName = "Grace Hopper", email = "grace@example.com"),
            ),
            roles = List("developer"),
            teams = Map("checkout" -> TeamMembership(access = "contributor", onCall = false)),
          ),
        ),
        projects = Map(
          "checkout-api" -> Project(
            repository = "github.com/example/checkout-api",
            maintainers = List("user-7", "user-11"),
            settings = Map("scala" -> "3.6.4", "deployRegion" -> "eu-west-2"),
          ),
          "risk-engine" -> Project(
            repository = "github.com/example/risk-engine",
            maintainers = List("user-7"),
            settings = Map("scala" -> "3.6.4", "deployRegion" -> "eu-west-1"),
          ),
        ),
        featureFlags = Map(
          "new-checkout" -> true,
          "risk-v2" -> false,
        ),
        audit = AuditMetadata(
          provisionedBy = "terraform-cloud",
          lastSyncedAt = "2026-07-20T09:41:12Z",
          revision = 18,
        ),
      ),
      expected = WorkspaceSnapshot(
        workspace = Workspace(
          id = "workspace-42",
          name = "payments-platform",
          owner = Person(
            id = "user-7",
            profile = Profile(displayName = "Ada Lovelace", email = "ada@example.com"),
          ),
          plan = Plan(name = "business", seatLimit = 30),
        ),
        members = List(
          Member(
            user = Person(
              id = "user-7",
              profile = Profile(displayName = "Ada Lovelace", email = "ada@example.com"),
            ),
            roles = List("admin", "developer", "billing"),
            teams = Map(
              "checkout" -> TeamMembership(access = "maintainer", onCall = true),
              "risk" -> TeamMembership(access = "contributor", onCall = false),
            ),
          ),
          Member(
            user = Person(
              id = "user-11",
              profile = Profile(displayName = "Grace Hopper", email = "grace@example.com"),
            ),
            roles = List("developer"),
            teams = Map("checkout" -> TeamMembership(access = "contributor", onCall = false)),
          ),
          Member(
            user = Person(
              id = "user-19",
              profile = Profile(displayName = "Margaret Hamilton", email = "margaret@example.com"),
            ),
            roles = List("developer"),
            teams = Map("risk" -> TeamMembership(access = "maintainer", onCall = true)),
          ),
        ),
        projects = Map(
          "checkout-api" -> Project(
            repository = "github.com/example/checkout-api",
            maintainers = List("user-7", "user-11", "user-19"),
            settings = Map("scala" -> "3.7.1", "deployRegion" -> "eu-west-2"),
          ),
          "risk-engine" -> Project(
            repository = "github.com/example/risk-engine",
            maintainers = List("user-7", "user-19"),
            settings = Map("scala" -> "3.6.4", "deployRegion" -> "eu-west-2"),
          ),
          "billing-worker" -> Project(
            repository = "github.com/example/billing-worker",
            maintainers = List("user-7"),
            settings = Map("scala" -> "3.7.1", "deployRegion" -> "eu-west-2"),
          ),
        ),
        featureFlags = Map(
          "new-checkout" -> true,
          "risk-v2" -> true,
          "invoice-emails" -> true,
        ),
        audit = AuditMetadata(
          provisionedBy = "terraform-cloud",
          lastSyncedAt = "2026-07-20T10:03:51Z",
          revision = 18,
        ),
      ),
    )
  }
}

object WorkspaceSuite {
  final case class WorkspaceSnapshot(
    workspace: Workspace,
    members: List[Member],
    projects: Map[String, Project],
    featureFlags: Map[String, Boolean],
    audit: AuditMetadata,
  )

  object WorkspaceSnapshot {
    given Differ[WorkspaceSnapshot] = Differ.derived[WorkspaceSnapshot].ignoreAt(_.audit.lastSyncedAt)
  }

  final case class Workspace(id: String, name: String, owner: Person, plan: Plan) derives Differ

  final case class Person(id: String, profile: Profile) derives Differ

  final case class Profile(displayName: String, email: String) derives Differ

  final case class Plan(name: String, seatLimit: Int) derives Differ

  final case class Member(
    user: Person,
    roles: List[String],
    teams: Map[String, TeamMembership],
  ) derives Differ

  final case class TeamMembership(access: String, onCall: Boolean) derives Differ

  final case class Project(
    repository: String,
    maintainers: List[String],
    settings: Map[String, String],
  ) derives Differ

  final case class AuditMetadata(provisionedBy: String, lastSyncedAt: String, revision: Int) derives Differ
}
