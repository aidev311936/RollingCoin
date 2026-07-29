tasks.register<Exec>("run") {
    group = "soundgen"
    description = "Starts a local HTTP server for the sound generator web app on port 8765"
    workingDir = file("webapp")
    commandLine("python3", "-m", "http.server", "8765")
    doFirst { println("\nSound Generator running at: http://localhost:8765\nPress Ctrl+C to stop.\n") }
}
