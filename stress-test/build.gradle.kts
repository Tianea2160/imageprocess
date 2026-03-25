plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gatling)
}

gatling {
    jvmArgs =
        listOf(
            "-server",
            "-Xms512M",
            "-Xmx2G",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
            "--enable-native-access=ALL-UNNAMED",
        )
}
