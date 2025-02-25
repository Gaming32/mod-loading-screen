# Mod Loading Screen

An advanced loading screen with the loading progress of mods. It works on all Minecraft versions, as it doesn't even require Minecraft. Its only requirement is Fabric Loader 0.12.0 or later or Quilt Loader (specific versions of Quilt support are unknown). Do note that if you run this mod on a game other than Minecraft, the loading screen may not close itself. 

## Agent

Mod Loading Screen provides a Java agent, which allows opening the loading screen before even the mod loader itself loads. This feature is primarily targeted at modpack developers who want to make a seamless loading experience. The agent can be used by passing `-javaagent:mod-loading-screen-1.0.5.jar` as a JVM argument. If the agent is used, the loading screen should *not* be installed as a mod.

## API

To depend on the API, use the Modrinth Maven. The API should be JiJed, and doing so will not include Mod Loading Screen inside your mod (it will only include the API, which is only a few kilobytes). The API is designed to have both forwards and backwards binary compatibility with future Mod Loading Screen versions. An API is provided for checking which API calls will return stubs and which ones are real.

```gradle
repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = "https://api.modrinth.com/maven"
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

dependencies {
    // implementation, not modImplementation!
    include(implementation("maven.modrinth:mod-loading-screen:1.0.5:api"))
}
```

The API has two top-level classes: `LoadingScreenApi` and `AvailableFeatures`. Full javadocs are available for both classes.
