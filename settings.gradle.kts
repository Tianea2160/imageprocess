rootProject.name = "imageprocess"

include(
    ":core:domain",
    ":core:application",
    ":infra:persistence",
    ":infra:redis",
    ":infra:mockworker",
    ":infra:kafka",
    ":app-api",
    ":app-consumer",
)
