-- Account deletion is a user-owned destructive operation. Repair any legacy
-- account foreign keys that were created without an explicit delete action.
-- Billing events intentionally retain history through SET NULL.
DO $$
DECLARE
    constraint_row RECORD;
    column_list TEXT;
    delete_action TEXT;
BEGIN
    FOR constraint_row IN
        SELECT
            c.conname,
            c.conrelid::regclass AS child_table,
            c.confdeltype,
            string_agg(quote_ident(child_att.attname), ', ' ORDER BY key_position.ordinality) AS child_columns
        FROM pg_constraint c
        JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS key_position(attnum, ordinality) ON TRUE
        JOIN pg_attribute child_att
            ON child_att.attrelid = c.conrelid
            AND child_att.attnum = key_position.attnum
        WHERE c.contype = 'f'
          AND c.confrelid = 'accounts'::regclass
        GROUP BY c.conname, c.conrelid, c.confdeltype
    LOOP
        column_list := constraint_row.child_columns;
        delete_action := CASE constraint_row.confdeltype
            WHEN 'n' THEN 'SET NULL'
            WHEN 'd' THEN 'SET DEFAULT'
            WHEN 'r' THEN 'RESTRICT'
            ELSE 'CASCADE'
        END;

        EXECUTE format(
            'ALTER TABLE %s DROP CONSTRAINT %I',
            constraint_row.child_table,
            constraint_row.conname
        );
        EXECUTE format(
            'ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (%s) REFERENCES accounts(id) ON DELETE %s',
            constraint_row.child_table,
            constraint_row.conname,
            column_list,
            delete_action
        );
    END LOOP;
END $$;
