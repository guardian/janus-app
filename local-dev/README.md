# Setting up Audit Database

When running locally, the audit endpoints require a Dynamo DB service to be running on port 8000.

The service hosts a database holding a table that stores audit logs.

To make this table available for testing auditing functionality:

1. Start Docker
2. Start up a local Dynamo DB container:
```shell
docker-compose up -d
```
3. Create the audit table schema by running [Creation test](test/aws/AuditTrailDBTest.scala#L46).
4. Populate the table with some test data by running [Insertion test](test/aws/AuditTrailDBTest.scala#L52).
