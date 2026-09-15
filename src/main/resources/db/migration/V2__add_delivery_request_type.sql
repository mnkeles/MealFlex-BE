ALTER TABLE public.delivery_modification_history
    ADD COLUMN request_type character varying(20) DEFAULT 'CHANGE' NOT NULL;

ALTER TABLE public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_request_type_check
        CHECK (request_type IN ('CHANGE', 'CANCEL'));
