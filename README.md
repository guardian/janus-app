Janus
=====

> In ancient Roman religion and myth, Janus (/ˈdʒeɪnəs/; Latin: Ianus,
> pronounced [ˈjaː.nus]) is the god of beginnings and transitions, and
> thereby of gates, doors, doorways, passages and endings.
>
> ... his tutelage extends to the covered passages named iani and
> foremost to the gates of the city, including the cultic gate of the
> Argiletum, named Ianus Geminus or Porta Ianualis from which he
> protects Rome against the Sabines. He is also present at the
> Sororium Tigillum, where he guards the terminus of the ways into
> Rome from Latium.
>
> --Wikipedia

Janus lets you use Google Authentication to provide audited temporary
access to AWS resources.

* [Features](#features)
* [Running Janus](#running-janus)
* [Configuration](#configuration)
* [Non-Guardian use](#non-guardian-use)
* [Publishing Janus Config Tools](#publishing-janus-config-tools)

## Features

### AWS access

* Designed for teams working across multiple AWS accounts
* Full IAM support gives complete control over the level of AWS access Janus can bestow
* Separate configuration for administrative access
* Support rota to grant extra access to staff working on support
* Rich type-safe configuration provided via `janus-config-tools` library

### Security

* Janus bestows short-lived temporary sessions
* All sessions are entered into a central audit log
* Users can be given tightly-controlled levels of access
* No need to manage IAM Users in AWS across multiple accounts
* Disable user's Google account to remove all AWS access
* Supports revocation of existing sessions for an account

### Web UI

* Simple to use
* Supports AWS console sessions as well as credentials

## Running Janus

Janus is a Scala application built on the Play Framework.

Wherever you run Janus, you need to provide three configuration files:

* A "janus data" configuration file that controls AWS Access
* the application configuration file required by the web framework
* a `service-account-cert.json` file for the Google Authentication

The configuration section (below) shows what is required.

### In AWS

This repository provides an [example CloudFormation template](docs/cloudformation/example.template.yaml).
The template will create all the AWS resources necessary for running
Janus in an AWS account, excluding the TLS certificate and the S3
bucket that will contain the Janus artifact.

**Note:** A DNS entry will be required so that the Google OAuth flow
can be used for the application. A hosts entry on your machine will
suffice for testing.

0. Upload a Janus artifact to S3
0. Modify the [example template](docs/cloudformation/example.template.yaml)
to provide valid UserData for the launch configuration and remove the
line that invalidates the template
0. Use AWS Certificate Manager to create an HTTPS certificate for the domain
0. Setup configuration as described below
0. Create a CloudFormation stack using your modified template
0. Point your DNS at Janus' Load Balancer
0. Use the [federation template](docs/cloudformation/federation-example.template.yaml) to integrate Janus with AWS accounts


### Locally

#### Janus AWS Profile

Janus requires an AWS profile called `janus` to exist in your local AWS
credentials file. Local dev is in a separate profile name so it is not
overwritten when you obtain credentials using Janus. The credentials do
not need to be real, you can get the application to run by adding the
following to your AWS credentials file:

```
[janus]
aws_access_key_id = FAKE000KEYID
aws_secret_access_key = FAKE000SECRETKEY
```

#### Install Java

You will need the Java JDK installed to run the Scala application
[check the JDK versions recommended for
Scala](https://www.scala-lang.org/download/).

#### Install sbt

Use Scala's build tool
([sbt](https://www.scala-sbt.org/download.html)) to build and run
Janus.

#### Obtain configuration

The configuration section below explains the requirements in more
detail

**Note:** Janus uses Google Authentication locally, you will need to
do some setup in Google as well.

#### Point your hostname at localhost:9100

This will likely involve DNS or a hosts entry as well as a webserver
(or container configuration) that forwards requests to port 9000.

#### Run Janus

Use sbt to run Janus in development mode. The server will
automatically recompile and reload when changes are made.

    sbt -Dconfig.file=<PATH>/janus.local.conf run

## Configuration

### Janus' AWS config

Janus' behaviour is configured using a Janus Data file, which is a
[HOCON](https://github.com/lightbend/config/blob/master/HOCON.md)
configuration file containing the data Janus expects. While it is
possible to write such a configuration file directly, Janus provides a
library `janus-config-tools` for reading and writing this file format.

The recommended way to create your Janus Data file is to create a
separate (private) Git repository that contains a Scala
application. This application can use the `janus-config-tools` library
to create a definition of the access Janus should be able to grant,
and output this information as a "Janus Data" file that the Janus
application can read.

Using a separate Git repository keeps your private Access lists out of
publicly available repositories. The Git log provides an immutable log
of Access changes, the janus-config-tools library provides a typesafe
way to write flexible and powerful configurations, and Scala's
ecosystem makes it easy to (for example) run tests over your
configuration.

This repository includes an example project, full documentation of
using `janus-config-tools` is included in [that project's
README](configTools/README.md).

### Application configuration

The Play Framework requires an application secret, and the Google
Authentication integration needs a few bits of configuration. These
are provided as a standard Play configuration file.

[The application's configuration file](conf/application.conf) shows
which fields are missing. Create a configuration file that provides
the missing values and includes the `application.config`.

### Google Auth

Instructions on configuring the Google Authentication for Janus are
available in the
[guardian/play-googleauth](https://github.com/guardian/play-googleauth)
library.

The configuration properties that come from the above steps should be
included in the application's configuration file, and the service
account certificate file will need to be available to the Janus
application.

## Non-Guardian use

The Guardian uses Janus to control access to its AWS accounts.  Our
usage of AWS is likely very similar to other organisations, you may
find Janus is a great fit for you as well.

### Usage

We recommend using this repository as inspiration for your own tool,
rather than hoping to run it exactly as-is. Even if Janus'
functionality is a good fit for your organisation, providing Janus as
an out-the-box experience is challenging. Answers to these questions
and others will likely differ between companies:

* how to deploy and run Janus
* how it fits into a CI pipeline
* how developers prefer to work locally.

If you do use the Janus application, we recommend the following
architecture:

#### In AWS

* Set up a single Security account, separate to your organisation's root account
* Run Janus here along with your other security-related tools
* Create a role that integrates Janus within each of your AWS accounts

#### For the application

* Build the Janus app from your own version of its source code
* Deploy Janus (with its configuration) to your Security AWS account
* Consider locking down access to your office(s) or VPN using Security Groups

#### The configuration

* Create a separate Scala project in its own Git repository to specify AWS access configuration
* Use your Git provider's Pull Request workflow to manage Access changes
* Produce a Janus Data file as a Continuous Integration step

### Contributions

Contributions are very welcome, thank you for taking the time to help
improve Janus.

Consider opening an issue to discuss features and ideas before
drafting a Pull Request.

## Publishing Janus Config Tools

You will need to be added to the Guardian organisation to be able to
publish updates. If you are not a Guardian employee and your company
would like to use a modified version of the library, you can publish
your own version under a different organisation by updating the
metadata in [the build.sbt file](build.sbt).

Updates can be published using `sbt`.

    sbt "project configTools" release

**NOTE:** It isn't possible for the sbt release plugin to push updates
to master because this app's Git repository is locked-down. After
performing the release you should raise a PR to share the updated
version number. The commits for this change will exist locally;
checkout a new branch, then push both the branch and relevant tags:

    git checkout -b <BRANCH NAME>
    git push origin <BRANCH NAME> --follow-tags
