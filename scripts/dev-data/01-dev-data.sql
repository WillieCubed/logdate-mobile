-- Development data for LogDate application
-- This script runs after migrations and sets up test data for local development

-- Insert development accounts
-- Note: In production, accounts are created through the WebAuthn registration flow
-- These are for development/testing purposes only

DO $$
BEGIN
    -- Only insert if no accounts exist (avoid duplicates on restart)
    IF NOT EXISTS (SELECT 1 FROM accounts LIMIT 1) THEN
        
        -- Development user 1
        INSERT INTO accounts (
            id, 
            username, 
            email, 
            display_name, 
            is_active, 
            created_at, 
            updated_at
        ) VALUES (
            gen_random_uuid(),
            'dev_user',
            'dev@logdate.app',
            'Development User',
            true,
            NOW(),
            NOW()
        );
        
        -- Development user 2  
        INSERT INTO accounts (
            id, 
            username, 
            email, 
            display_name, 
            is_active, 
            created_at, 
            updated_at
        ) VALUES (
            gen_random_uuid(),
            'test_user',
            'test@logdate.app', 
            'Test User',
            true,
            NOW(),
            NOW()
        );
        
        RAISE NOTICE 'Development accounts created successfully';
        
    ELSE
        RAISE NOTICE 'Accounts already exist, skipping development data insertion';
    END IF;
    
END $$;