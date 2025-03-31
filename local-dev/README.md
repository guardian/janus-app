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


## Setting up Passkeys tables

To test registration of passkeys and their use in authentication, you will need to have the tables to hold passkey
public key data and challenges in your local database.  
To make these tables available for testing passkeys functionality:    
1. Create the passkey credentials table schema by running [create table test](/test/aws/PasskeyDBTest.scala).
2. Create the passkey challenges table schema by running [create table test](/test/aws/PasskeyChallengeDBTest.scala).

These tests are ignored by default.  To run them, just change `ignore` to `in`.
