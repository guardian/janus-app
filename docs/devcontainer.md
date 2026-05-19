# Devcontainer Changes

Docker needs to be started (see below for first run changes)

The data location in docker-compose needs to change:

 * /workspaces/janus-app/data:/home/dynamodblocal/data for vscode
 * /IdeaProjects/janus-app/data:/home/dynamodblocal/data for IntelliJ

The create tables "test" will then need to be run.

# Devcontainer Temporary Issues

Currently the devcontainer has a 2.3 version of containerd installed by the plugin, but this is not compatible
with the docker version.  As a result the docker daemon is not started.

Use
```
sudo apt-get update && sudo apt-get install -y --allow-downgrades moby-containerd=1.7.30-ubuntu24.04u2
```
and then start docker manually with `sudo dockerd`.

`sbt run` with a config file specification should now work.
