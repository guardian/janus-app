# Setting up Audit Database

When running locally, the audit endpoints require a Dynamo DB service to be running on port 8000.

The service hosts a database holding a table that stores audit logs.

To make this table available for testing auditing functionality:

1. Start Docker
2. Start up a local Dynamo DB container:
```shell
cd local-dev
docker-compose up -d
```
3. Create the audit table schema by running [Creation test](https://github.com/guardian/janus-app/blob/f522bca83d9d90657634b038c7734a9871974161/test/aws/AuditTrailDBTest.scala#L48-L50).
4. Populate the table with some test data by running [Insertion test](https://github.com/guardian/janus-app/blob/f522bca83d9d90657634b038c7734a9871974161/test/aws/AuditTrailDBTest.scala#L16-L46).
