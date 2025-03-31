# Setting up a local Dynamo DB service

You will need to have Docker installed to run the local Dynamo DB service.  
Then:  
1. Start Docker
2. Start up a local Dynamo DB container:
```shell
cd local-dev
docker-compose up -d
```

## Setting up Audit table

The service hosts a database holding a table that stores audit logs.  
To make this table available for testing auditing functionality:  
1. Create the audit table schema by running [Creation test](https://github.com/guardian/janus-app/blob/f522bca83d9d90657634b038c7734a9871974161/test/aws/AuditTrailDBTest.scala#L48-L50).
2. Populate the table with some test data by running [Insertion test](https://github.com/guardian/janus-app/blob/f522bca83d9d90657634b038c7734a9871974161/test/aws/AuditTrailDBTest.scala#L16-L46).


## Setting up Passkeys table

We also host a table that holds passkey public keys for authentication.  
To make this table available for testing Passkeys functionality:  
Create the passkey table schema by running [create table test](/test/aws/PasskeyDBTest.scala).
