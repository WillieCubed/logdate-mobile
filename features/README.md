# Features Module

[TODO: Migration in progress]

This module contains implementations of features used throughout all LogDate client applications.

*For brevity sake, all of the module names assume an implicit `:client` prefix
as this is a monorepo that shares some code between the multiplatform client and
the LogDate Cloud back-end `:server`.*

Generally, the hierarchy of dependencies for an app module may look something like this:

- `:app:compose-main` - Client app
    - `:feature:onboarding` - Wrapper for all onboarding-related features
        - `:feature:onboarding:domain` - Definitions for domain logic
            - `:feature:onboarding:shared` - Internal data models, core interfaces
        - `:feature:onboarding:data` - Repository implementations
            - `:feature:onboarding:shared`
            - `:core:database`
        - `:feature:onboarding:ui`
            - `:feature:shared-ui`
              - `:feature:shared-ui:theme`
              - `:feature:shared-ui:`

Generally, UI is kind of the exception for our module hierarchy. 

# App (`:app`) Modules

App modules are meant to be used to generate final artifacts that can be run on
an end user's device. These are responsible for "assembling" feature modules to
deliver cohesive product experiences. They act as routers, handling
functionality like navigating between routes and providing entry points to
platform-specific APIs.

App modules may depend on any client module or modules shared with the server,
but generally, app modules mainly depend on feature modules to expose
functionality.

`:app` modules depend on :`feature` modules that depend on `:core` modules and `:shared`
modules. This allows for a clean separation of concerns and modularity in the codebase.

# Feature (`:feature`) Modules

## Overview

Feature modules are those that exist to fulfill *experiences* that are directly
accessible to an end user.

Feature modules can be composed when needed to speed up compilation. Features
may depend on each other, but generally, one feature should only use the
relevant submodules.

For example, the timeline feature

Generally, the `:ui` component of a feature module should expose any top-level
functionality that is meant to be bound to a system interface (e.g.
screen-level UI composables that a client can navigate to.

Feature modules should not nest features with related but mutually exclusive
functionality. For example, a feature on one platform may only be available on
another. Alternatively, implementations of a feature in concept may differ
substantially, resulting in little reason to group functionality together. For
example, onboarding may be a single "feature" of the app in a general sense, but
because the onboarding experience on a handheld mobile device requires
an incompatible set of components than onboarding on a smartphone, it would not
make sense to define a single `:feature:onboarding` module for both. Instead,
we would separate onboarding components for mobile and Wear OS into
`:feature:onboarding` and `:feature:onboarding-wearos`. It would be, however,
perfectly acceptable to have both of those features depend on shared
`:feature:onboarding:data` and `:feature:onboarding:core` modules if enough
of the underlying data structures were shared.

Features are meant to be separated to accommodate the variety of product
surfaces through with LogDate may be accessible. For example, the Android
Instant App experience may need fewer dependencies, resulting in a tree that
looks like this:

- `:app:wear`
    - `:feature:auth`
    - `:feature:editor`
    - `:feature:audio`
    - `:feature:onboarding-wearos`

## Data Submodules

*Deprecation note:*
The app used to use a separate `:repository` module to define all the repositories
in the app, but given the increasing complexity of the app, this was an anti-
pattern. As such, the app is moving away from a central repository layer and is
instead relying on more explicit dependencies between smaller modules to depend
on interface definitions.

# Core (`:core`) Modules

Core modules are those that solely exist to provide lower-level API surfaces to
implement feature modules and other higher-level client functionality. These do
not provide UI. This includes functionality like wrappers for basic HTTP networking,
an SQLite database layer for the app, and utilities for managing app system
permissions.