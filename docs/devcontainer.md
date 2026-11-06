# Devcontainer Approach

NB Docker needs to be started

The objective is to provide a simple repeatable development environment following the following steps:

* configure dev container (done for you)
* start dev container
* get `Run Locally` dev profile credentials
* get setup artefacts (requires credentials)
* start project (requires credentials)
* *develop*

As, for example, the create tables "test" needs to be run, we should endeavour to provide breadcrumbs in the setup
and start scripts to check for all the known gotchas.
