# Google Services for Library Gradle Plugin

This plugin creates a function `fun googleServicesOptionsBuilder():FirebaseOptions.Builder` which provides a
FirebaseOptions.Builder object pre-populated with the values from the google-services.json file.

This plugin is non-drop-in replacement for the official [Google Services Gradle Plugin](https://github.com/google/play-services-plugins/tree/main).
It is intended to be used in androidTest within libraries modules where the 
original plugin is [not applicable](https://github.com/google/play-services-plugins/blob/main/google-services-plugin/README.md#compatible-android-plugins).

## Usage

### Plugins DSL

Add the following to your project's settings.gradle:

```
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}
```

Apply the plugin in your app's build.gradle.kts:

```
plugins {
    id("solutions.s4y.google-services") version "1.0.0-alpha01"
}
```

Or in build.gradle:
```
plugins {
    id 'solutions.s4y.google-services' version '1.0.0-alpha01'
}
```


#### Plugin configuration

Configure the plugin's behavior through the `googleServiceLibrary` block in build.gradle.kts:

```
googleServiceLibrary {
    // Application ID from Firebase console (required)
    // it will be used to find the app data in google-services.json 
    googleCloudAppId = "you.domain.app"
    
    // package name for the `fun googleServicesOptionsBuilder():FirebaseOptions.Builder` function
    // if not set, it will be the same as the `googleCloudAppId`
    
    classPackage = "com.example"
}
```

#### Created artifact

To the `build/generated` directory will be created a file `googleServicesOptionsBuilder.kt` with the content like this:

```kotlin
package your.application.id
import com.google.firebase.FirebaseOptions

fun googleServicesOptionsBuilder():FirebaseOptions.Builder =
   FirebaseOptions.Builder()
     .setApiKey("sOMeR4nD0mK3y")
     .setProjectId("app-b838e")
     .setApplicationId("1:481001888013:android:1828764839b7af18da58be")
     .setDatabaseUrl("null")
     .setStorageBucket("app-b838e.appspot.com")
```

This function can be used in your tests to initialize the FirebaseApp:

```kotlin
private var initialized = false
fun firebaseInit() {
    if (!initialized) {
        val builder: FirebaseOptions.Builder = googleServicesOptionsBuilder()
        val options = builder.build()
        val app = ApplicationProvider.getApplicationContext<Application>()
        FirebaseApp.initializeApp(app, options, "[DEFAULT]")
        initialized = true
    }
}
```