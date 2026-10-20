"""Exercise the reader boundary and account triggers on disposable PostgreSQL."""
from pathlib import Path
import os
import subprocess
import time
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[3]
MIGRATIONS = ROOT / 'server/src/main/resources/db/migration'
BOUNDARY = MIGRATIONS / 'V49__Private_account_trigger_functions.sql'


class FunctionPrivilegesPostgresTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = 'logdate-reader-test-' + uuid.uuid4().hex[:12]
        subprocess.run([
            'docker', 'run', '--detach', '--rm', '--name', cls.container,
            '-e', 'POSTGRES_HOST_AUTH_METHOD=trust',
            os.environ.get('LOGDATE_TEST_POSTGRES_IMAGE', 'postgres:18-alpine'),
        ], check=True, capture_output=True, text=True)
        cls.addClassCleanup(subprocess.run, ['docker', 'rm', '--force', cls.container],
                            check=True, capture_output=True, text=True)
        for _ in range(60):
            ready = subprocess.run(['docker', 'exec', cls.container, 'pg_isready', '-h', '127.0.0.1', '-U', 'postgres'],
                                   capture_output=True)
            if ready.returncode == 0:
                break
            time.sleep(0.2)
        else:
            raise RuntimeError('Disposable PostgreSQL did not become ready')
        cls.execute('CREATE ROLE app_owner LOGIN; CREATE ROLE named_executor; '
                    'CREATE ROLE reporting_reader; CREATE DATABASE boundary OWNER app_owner;')
        cls.execute('SET ROLE app_owner; GRANT USAGE ON SCHEMA public TO named_executor, reporting_reader; '
                    'ALTER DEFAULT PRIVILEGES GRANT EXECUTE ON FUNCTIONS TO named_executor;', 'boundary')
        # Apply actual product migrations, each transactionally like Flyway. V41 is the
        # production baseline; the same boundary must survive V42's CREATE OR REPLACE.
        cls.apply(MIGRATIONS / 'beforeMigrate.sql')
        for migration in sorted(MIGRATIONS.glob('V*__*.sql'), key=cls.version):
            if cls.version(migration) <= 41:
                cls.apply(migration)
        cls.apply(BOUNDARY)
        cls.apply(BOUNDARY)  # Future canonical delivery after the emergency transaction.

    @staticmethod
    def version(path):
        return int(path.name.split('__')[0][1:])

    @classmethod
    def execute(cls, statement, database='postgres', check=True):
        result = subprocess.run(['docker', 'exec', '-i', cls.container, 'psql', '-X', '-A', '-t',
                                 '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', database],
                                input=statement, capture_output=True, text=True)
        if check and result.returncode:
            raise AssertionError(result.stderr)
        return result

    @classmethod
    def apply(cls, path):
        cls.execute('BEGIN; SET LOCAL ROLE app_owner;\n' + path.read_text() + '\nCOMMIT;', 'boundary')

    def test_existing_and_future_trigger_rights_preserve_named_grants(self):
        query = """
SELECT bool_and(NOT has_function_privilege('reporting_reader', oid, 'EXECUTE'))
 AND bool_and(has_function_privilege('app_owner', oid, 'EXECUTE'))
 AND bool_and(has_function_privilege('named_executor', oid, 'EXECUTE'))
FROM pg_proc WHERE proname IN ('enqueue_account_blob_cleanup', 'prevent_deleted_account_id_reuse');
SET ROLE app_owner;
CREATE FUNCTION future_product_function() RETURNS boolean LANGUAGE sql AS 'SELECT true';
RESET ROLE;
SELECT NOT has_function_privilege('reporting_reader', 'future_product_function()', 'EXECUTE')
 AND has_function_privilege('named_executor', 'future_product_function()', 'EXECUTE');
"""
        self.assertEqual(['t', 'SET', 'CREATE FUNCTION', 'RESET', 't'],
                         self.execute(query, 'boundary').stdout.strip().splitlines())

    def test_account_creation_uuid_default_delete_and_reuse_fence(self):
        self.assert_account_behavior('baseline')
        for migration in sorted(MIGRATIONS.glob('V*__*.sql'), key=self.version):
            if 42 <= self.version(migration) <= 48:
                self.apply(migration)
        self.apply(BOUNDARY)
        self.assert_account_behavior('latest')

    def test_flyway_selected_schema_keeps_migration_isolated(self):
        self.execute('CREATE SCHEMA isolated AUTHORIZATION app_owner;', 'boundary')
        for path in [MIGRATIONS / 'V1__Initial_schema.sql',
                     MIGRATIONS / 'V40__Durable_account_blob_cleanup.sql', BOUNDARY]:
            self.execute('BEGIN; SET LOCAL ROLE app_owner; '
                         'SET LOCAL search_path=isolated,public;\n' + path.read_text() + '\nCOMMIT;',
                         'boundary')
        self.assertEqual('t', self.execute("""
SELECT count(*)=2
 AND bool_and(NOT has_function_privilege('reporting_reader', p.oid, 'EXECUTE'))
 AND bool_and(has_function_privilege('app_owner', p.oid, 'EXECUTE'))
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='isolated' AND p.proname IN
 ('enqueue_account_blob_cleanup', 'prevent_deleted_account_id_reuse');
""", 'boundary').stdout.strip())

    def assert_account_behavior(self, label):
        account_id = str(uuid.uuid4())
        statements = f"""
SET ROLE app_owner;
INSERT INTO accounts(id, username, display_name) VALUES ('{account_id}', '{label}', 'Fixture');
INSERT INTO passkeys(account_id, credential_id, public_key)
 VALUES ('{account_id}', '{label}', 'fixture');
SELECT id IS NOT NULL FROM passkeys WHERE account_id='{account_id}';
DELETE FROM accounts WHERE id='{account_id}';
SELECT EXISTS(SELECT 1 FROM account_blob_cleanup WHERE owner_id='{account_id}')
 AND NOT EXISTS(SELECT 1 FROM passkeys WHERE account_id='{account_id}');
"""
        output = self.execute(statements, 'boundary').stdout.strip().splitlines()
        self.assertEqual(['SET', 'INSERT 0 1', 'INSERT 0 1', 't', 'DELETE 1', 't'], output)
        blocked = self.execute(f"SET ROLE app_owner; INSERT INTO accounts(id, username, display_name) "
                               f"VALUES ('{account_id}', 'reuse{label}', 'Fixture');", 'boundary', check=False)
        self.assertNotEqual(0, blocked.returncode)
        self.assertIn('Deleted account identifiers cannot be reused', blocked.stderr)


if __name__ == '__main__':
    unittest.main()
