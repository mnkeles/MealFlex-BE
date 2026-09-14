-- MealFlex production baseline schema.
--
-- This migration is a squashed snapshot of the schema produced by the former
-- V1 through V65 migrations. It deliberately contains no development accounts
-- or other seed data. Run db/seed/dev_test_data.sql only in local development.
--
-- PostgreSQL database dump
--

-- Dumped from database version 15.2
-- Dumped by pg_dump version 15.2

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: addresses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.addresses (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(100) NOT NULL,
    city character varying(50) NOT NULL,
    district character varying(50) NOT NULL,
    neighborhood character varying(100),
    street character varying(200),
    building_no character varying(20),
    floor character varying(10),
    apartment_no character varying(10),
    full_address text,
    directions text,
    latitude numeric(10,7) NOT NULL,
    longitude numeric(10,7) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    default_address boolean DEFAULT false NOT NULL
);


--
-- Name: addresses_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.addresses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: addresses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.addresses_id_seq OWNED BY public.addresses.id;


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    actor_id bigint NOT NULL,
    action character varying(100) NOT NULL,
    entity_type character varying(50) NOT NULL,
    entity_id bigint NOT NULL,
    old_value text,
    new_value text,
    "timestamp" timestamp with time zone DEFAULT now() NOT NULL,
    correlation_id character varying(100)
);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;


--
-- Name: automation_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.automation_tasks (
    id bigint NOT NULL,
    task_type character varying(80) NOT NULL,
    payload text,
    status character varying(30) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    run_after timestamp with time zone NOT NULL,
    last_error character varying(1000),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: automation_tasks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.automation_tasks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: automation_tasks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.automation_tasks_id_seq OWNED BY public.automation_tasks.id;


--
-- Name: bank_catalog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bank_catalog (
    id bigint NOT NULL,
    name character varying(120) NOT NULL,
    legal_name character varying(255) NOT NULL,
    bank_type character varying(30) NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: bank_catalog_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bank_catalog_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bank_catalog_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bank_catalog_id_seq OWNED BY public.bank_catalog.id;


--
-- Name: business_hours; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.business_hours (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    day_of_week character varying(10) NOT NULL,
    open boolean DEFAULT true NOT NULL,
    open_time time without time zone,
    close_time time without time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: business_hours_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.business_hours_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: business_hours_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.business_hours_id_seq OWNED BY public.business_hours.id;


--
-- Name: campaign_redemptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.campaign_redemptions (
    id bigint NOT NULL,
    campaign_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    subscription_id bigint,
    discount_amount numeric(12,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: campaign_redemptions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.campaign_redemptions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: campaign_redemptions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.campaign_redemptions_id_seq OWNED BY public.campaign_redemptions.id;


--
-- Name: campaigns; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.campaigns (
    id bigint NOT NULL,
    store_id bigint,
    menu_id bigint,
    code character varying(50),
    name character varying(160) NOT NULL,
    campaign_type character varying(30) NOT NULL,
    discount_value numeric(12,2) DEFAULT 0 NOT NULL,
    min_amount numeric(12,2),
    max_uses_per_customer integer DEFAULT 1 NOT NULL,
    first_subscription_only boolean DEFAULT false NOT NULL,
    corporate_code character varying(80),
    corporate_price_per_person numeric(12,2),
    referral_reward numeric(12,2),
    start_date date NOT NULL,
    end_date date NOT NULL,
    active boolean DEFAULT true NOT NULL,
    seller_share_rate numeric(5,4) DEFAULT 0 NOT NULL,
    platform_share_rate numeric(5,4) DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    target_customer_id bigint
);


--
-- Name: campaigns_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.campaigns_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: campaigns_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.campaigns_id_seq OWNED BY public.campaigns.id;


--
-- Name: commission_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.commission_rules (
    id bigint NOT NULL,
    store_id bigint,
    commission_rate numeric(7,4) NOT NULL,
    commission_vat_rate numeric(7,4) NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: commission_rules_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.commission_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: commission_rules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.commission_rules_id_seq OWNED BY public.commission_rules.id;


--
-- Name: complaint_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.complaint_attachments (
    id bigint NOT NULL,
    complaint_id bigint NOT NULL,
    file_name character varying(255) NOT NULL,
    storage_name character varying(255) NOT NULL,
    content_type character varying(100) NOT NULL,
    file_size bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: complaint_attachments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.complaint_attachments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: complaint_attachments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.complaint_attachments_id_seq OWNED BY public.complaint_attachments.id;


--
-- Name: complaints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.complaints (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    store_id bigint NOT NULL,
    subscription_id bigint,
    delivery_id bigint,
    reason character varying(100) NOT NULL,
    description text NOT NULL,
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    admin_note text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    seller_response text,
    escalated_at timestamp with time zone,
    attachment_urls text,
    customer_message text,
    internal_note text,
    resolution_type character varying(30),
    resolution_amount numeric(12,2),
    compensation_code character varying(80),
    resolved_at timestamp with time zone,
    resolved_by_user_id bigint
);


--
-- Name: complaints_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.complaints_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: complaints_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.complaints_id_seq OWNED BY public.complaints.id;


--
-- Name: consent_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.consent_records (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    document_type character varying(40) NOT NULL,
    document_version character varying(30) NOT NULL,
    accepted_at timestamp with time zone NOT NULL,
    ip_address character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: consent_records_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.consent_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: consent_records_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.consent_records_id_seq OWNED BY public.consent_records.id;


--
-- Name: couriers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.couriers (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    full_name character varying(150) NOT NULL,
    phone character varying(40),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    email character varying(255)
);


--
-- Name: couriers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.couriers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: couriers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.couriers_id_seq OWNED BY public.couriers.id;


--
-- Name: customer_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_profiles (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    company_name character varying(255),
    tax_number character varying(20),
    tax_office character varying(100),
    invoice_address text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: customer_profiles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_profiles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_profiles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_profiles_id_seq OWNED BY public.customer_profiles.id;


--
-- Name: delivery_modification_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delivery_modification_history (
    id bigint NOT NULL,
    subscription_id bigint NOT NULL,
    delivery_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    old_address_id bigint,
    new_address_id bigint,
    old_menu_id bigint,
    new_menu_id bigint,
    old_delivery_time time without time zone,
    new_delivery_time time without time zone,
    old_person_count integer,
    new_person_count integer,
    price_difference numeric(12,2) DEFAULT 0 NOT NULL,
    payment_id bigint,
    refund_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    request_status character varying(20) DEFAULT 'APPLIED'::character varying NOT NULL,
    decision_reason character varying(500),
    decided_at timestamp with time zone,
    decided_by_user_id bigint,
    deferred_reduction numeric(12,2) DEFAULT 0 NOT NULL,
    customer_note character varying(500)
);


--
-- Name: delivery_modification_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.delivery_modification_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: delivery_modification_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.delivery_modification_history_id_seq OWNED BY public.delivery_modification_history.id;


--
-- Name: delivery_proofs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delivery_proofs (
    id bigint NOT NULL,
    delivery_id bigint NOT NULL,
    storage_name character varying(255) NOT NULL,
    content_type character varying(100) NOT NULL,
    file_size bigint NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: delivery_proofs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.delivery_proofs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: delivery_proofs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.delivery_proofs_id_seq OWNED BY public.delivery_proofs.id;


--
-- Name: favorites; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.favorites (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    store_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: favorites_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.favorites_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: favorites_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.favorites_id_seq OWNED BY public.favorites.id;


--
-- Name: feature_flags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feature_flags (
    id bigint NOT NULL,
    flag_key character varying(100) NOT NULL,
    description character varying(500),
    enabled boolean DEFAULT false NOT NULL,
    rollout_percent integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: feature_flags_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.feature_flags_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: feature_flags_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.feature_flags_id_seq OWNED BY public.feature_flags.id;


--
-- Name: finance_reconciliations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_reconciliations (
    id bigint NOT NULL,
    reconciliation_date date NOT NULL,
    provider_collected_amount numeric(12,2),
    ledger_collected_amount numeric(12,2) DEFAULT 0 NOT NULL,
    paid_payout_amount numeric(12,2) DEFAULT 0 NOT NULL,
    discrepancy_amount numeric(12,2),
    status character varying(30) DEFAULT 'PROVIDER_UNAVAILABLE'::character varying NOT NULL,
    assigned_admin_id bigint,
    resolution_note character varying(1000),
    resolved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: finance_reconciliations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finance_reconciliations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finance_reconciliations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finance_reconciliations_id_seq OWNED BY public.finance_reconciliations.id;


--
-- Name: integration_api_keys; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integration_api_keys (
    id bigint NOT NULL,
    name character varying(120) NOT NULL,
    key_prefix character varying(16) NOT NULL,
    key_hash character varying(255) NOT NULL,
    scopes character varying(500) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    last_used_at timestamp with time zone,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: integration_api_keys_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.integration_api_keys_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: integration_api_keys_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.integration_api_keys_id_seq OWNED BY public.integration_api_keys.id;


--
-- Name: invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoices (
    id bigint NOT NULL,
    payment_id bigint NOT NULL,
    subscription_id bigint NOT NULL,
    invoice_number character varying(60) NOT NULL,
    invoice_type character varying(30) NOT NULL,
    currency character varying(3) DEFAULT 'TRY'::character varying NOT NULL,
    gross_amount numeric(12,2) NOT NULL,
    document_url character varying(500),
    issued_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    provider character varying(40) DEFAULT 'LOCAL'::character varying NOT NULL,
    provider_document_id character varying(150),
    delivery_status character varying(30) DEFAULT 'PENDING_ISSUANCE'::character varying NOT NULL,
    emailed_at timestamp with time zone
);


--
-- Name: invoices_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invoices_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.invoices_id_seq OWNED BY public.invoices.id;


--
-- Name: meal_balance_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_balance_accounts (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    currency character varying(3) DEFAULT 'TRY'::character varying NOT NULL,
    available_amount numeric(12,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: meal_balance_accounts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.meal_balance_accounts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: meal_balance_accounts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.meal_balance_accounts_id_seq OWNED BY public.meal_balance_accounts.id;


--
-- Name: meal_balance_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meal_balance_transactions (
    id bigint NOT NULL,
    account_id bigint NOT NULL,
    subscription_id bigint,
    payment_id bigint,
    delivery_id bigint,
    type character varying(40) NOT NULL,
    amount numeric(12,2) NOT NULL,
    balance_after numeric(12,2) NOT NULL,
    reference_key character varying(160) NOT NULL,
    description character varying(300),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: meal_balance_transactions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.meal_balance_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: meal_balance_transactions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.meal_balance_transactions_id_seq OWNED BY public.meal_balance_transactions.id;


--
-- Name: menu_allergens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.menu_allergens (
    menu_id bigint NOT NULL,
    allergen character varying(60) NOT NULL
);


--
-- Name: menu_diet_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.menu_diet_tags (
    menu_id bigint NOT NULL,
    diet_tag character varying(60) NOT NULL
);


--
-- Name: menu_gallery_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.menu_gallery_images (
    id bigint NOT NULL,
    menu_id bigint NOT NULL,
    image_url character varying(500) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: menu_gallery_images_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.menu_gallery_images_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: menu_gallery_images_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.menu_gallery_images_id_seq OWNED BY public.menu_gallery_images.id;


--
-- Name: menu_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.menu_items (
    id bigint NOT NULL,
    menu_id bigint NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    image_url character varying(500),
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: menu_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.menu_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: menu_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.menu_items_id_seq OWNED BY public.menu_items.id;


--
-- Name: menu_schedule_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.menu_schedule_versions (
    id bigint NOT NULL,
    menu_version_id bigint NOT NULL,
    snapshot_json text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: menu_schedule_versions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.menu_schedule_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: menu_schedule_versions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.menu_schedule_versions_id_seq OWNED BY public.menu_schedule_versions.id;


--
-- Name: menu_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.menu_schedules (
    id bigint NOT NULL,
    menu_id bigint NOT NULL,
    day_of_week character varying(10) NOT NULL,
    item_name character varying(200) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: menu_schedules_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.menu_schedules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: menu_schedules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.menu_schedules_id_seq OWNED BY public.menu_schedules.id;


--
-- Name: menu_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.menu_versions (
    id bigint NOT NULL,
    menu_id bigint NOT NULL,
    version_number integer NOT NULL,
    effective_from date NOT NULL,
    price_per_person numeric(10,2) NOT NULL,
    snapshot_json text NOT NULL,
    created_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: menu_versions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.menu_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: menu_versions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.menu_versions_id_seq OWNED BY public.menu_versions.id;


--
-- Name: menus; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.menus (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    price_per_person numeric(10,2) NOT NULL,
    image_url character varying(500),
    allergen_info text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    price_effective_from date DEFAULT CURRENT_DATE NOT NULL,
    available_from date,
    available_until date,
    CONSTRAINT chk_menu_availability_period CHECK (((available_from IS NULL) OR (available_until IS NULL) OR (available_until >= available_from)))
);


--
-- Name: menus_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.menus_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: menus_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.menus_id_seq OWNED BY public.menus.id;


--
-- Name: notification_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_events (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    event_type character varying(80) NOT NULL,
    channels character varying(100) NOT NULL,
    title character varying(200) NOT NULL,
    body text NOT NULL,
    reference_type character varying(50),
    reference_id bigint,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone,
    last_error text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    delivered_channels character varying(100)
);


--
-- Name: notification_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_events_id_seq OWNED BY public.notification_events.id;


--
-- Name: notification_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_preferences (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    email_enabled boolean DEFAULT true NOT NULL,
    sms_enabled boolean DEFAULT true NOT NULL,
    push_enabled boolean DEFAULT true NOT NULL,
    marketing_enabled boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: notification_preferences_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_preferences_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_preferences_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_preferences_id_seq OWNED BY public.notification_preferences.id;


--
-- Name: notification_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_templates (
    id bigint NOT NULL,
    template_code character varying(80) NOT NULL,
    title_template character varying(200) NOT NULL,
    body_template text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: notification_templates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_templates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_templates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_templates_id_seq OWNED BY public.notification_templates.id;


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(200) NOT NULL,
    message text NOT NULL,
    read boolean DEFAULT false NOT NULL,
    read_at timestamp with time zone,
    reference_type character varying(50),
    reference_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notifications_id_seq OWNED BY public.notifications.id;


--
-- Name: payment_allocations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_allocations (
    id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone,
    payment_id bigint NOT NULL,
    delivery_id bigint NOT NULL,
    amount numeric(12,2) NOT NULL,
    returned_amount numeric(12,2) DEFAULT 0 NOT NULL,
    CONSTRAINT payment_allocations_amount_check CHECK ((amount >= (0)::numeric)),
    CONSTRAINT payment_allocations_check CHECK (((returned_amount >= (0)::numeric) AND (returned_amount <= amount)))
);


--
-- Name: payment_allocations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payment_allocations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_allocations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payment_allocations_id_seq OWNED BY public.payment_allocations.id;


--
-- Name: payment_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_attempts (
    id bigint NOT NULL,
    payment_id bigint NOT NULL,
    attempt_number integer NOT NULL,
    status character varying(30) NOT NULL,
    provider_request_id character varying(255),
    provider_response_code character varying(80),
    failure_message character varying(500),
    attempted_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: payment_attempts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payment_attempts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_attempts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payment_attempts_id_seq OWNED BY public.payment_attempts.id;


--
-- Name: payment_card_management_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_card_management_sessions (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    provider character varying(40) NOT NULL,
    conversation_id character varying(100) NOT NULL,
    external_id character varying(100) NOT NULL,
    provider_token character varying(500) NOT NULL,
    card_page_url text NOT NULL,
    status character varying(30) NOT NULL,
    client_ip character varying(64) NOT NULL,
    expires_at timestamp with time zone,
    failure_code character varying(100),
    failure_message character varying(500),
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: payment_card_management_sessions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payment_card_management_sessions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_card_management_sessions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payment_card_management_sessions_id_seq OWNED BY public.payment_card_management_sessions.id;


--
-- Name: payment_checkout_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_checkout_sessions (
    id bigint NOT NULL,
    subscription_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    provider character varying(40) NOT NULL,
    conversation_id character varying(100) NOT NULL,
    provider_token character varying(500) NOT NULL,
    payment_page_url text NOT NULL,
    status character varying(30) NOT NULL,
    amount numeric(12,2) NOT NULL,
    currency character varying(3) NOT NULL,
    week_start date NOT NULL,
    client_ip character varying(64) NOT NULL,
    expires_at timestamp with time zone,
    provider_payment_id character varying(255),
    failure_code character varying(100),
    failure_message character varying(500),
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: payment_checkout_sessions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payment_checkout_sessions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_checkout_sessions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payment_checkout_sessions_id_seq OWNED BY public.payment_checkout_sessions.id;


--
-- Name: payment_methods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_methods (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    provider character varying(40) NOT NULL,
    provider_token character varying(255) NOT NULL,
    card_holder_name character varying(150),
    brand character varying(40) NOT NULL,
    last_four character varying(4) NOT NULL,
    expiry_month integer NOT NULL,
    expiry_year integer NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    provider_customer_token character varying(500),
    registration_ip character varying(64)
);


--
-- Name: payment_methods_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payment_methods_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_methods_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payment_methods_id_seq OWNED BY public.payment_methods.id;


--
-- Name: payment_webhook_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_webhook_events (
    id bigint NOT NULL,
    provider character varying(40) NOT NULL,
    provider_event_id character varying(255) NOT NULL,
    event_type character varying(100) NOT NULL,
    payload_hash character varying(64) NOT NULL,
    status character varying(30) NOT NULL,
    processed_at timestamp with time zone,
    error_message character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: payment_webhook_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payment_webhook_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_webhook_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payment_webhook_events_id_seq OWNED BY public.payment_webhook_events.id;


--
-- Name: payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payments (
    id bigint NOT NULL,
    subscription_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    store_id bigint NOT NULL,
    payment_method_id bigint,
    status character varying(30) NOT NULL,
    provider character varying(40) NOT NULL,
    provider_payment_id character varying(255),
    idempotency_key character varying(100) NOT NULL,
    currency character varying(3) DEFAULT 'TRY'::character varying NOT NULL,
    gross_amount numeric(12,2) NOT NULL,
    commission_amount numeric(12,2) DEFAULT 0 NOT NULL,
    commission_tax_amount numeric(12,2) DEFAULT 0 NOT NULL,
    refunded_amount numeric(12,2) DEFAULT 0 NOT NULL,
    net_amount numeric(12,2) DEFAULT 0 NOT NULL,
    failure_code character varying(80),
    failure_message character varying(500),
    paid_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    balance_amount numeric(12,2) DEFAULT 0 NOT NULL,
    card_amount numeric(12,2) DEFAULT 0 NOT NULL,
    collection_failed_at timestamp with time zone,
    next_retry_at timestamp with time zone
);


--
-- Name: payments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payments_id_seq OWNED BY public.payments.id;


--
-- Name: platform_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_settings (
    id bigint NOT NULL,
    setting_key character varying(100) NOT NULL,
    setting_value character varying(500) NOT NULL,
    description character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: platform_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.platform_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: platform_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.platform_settings_id_seq OWNED BY public.platform_settings.id;


--
-- Name: product_analytics_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_analytics_events (
    id bigint NOT NULL,
    event_name character varying(100) NOT NULL,
    actor_id bigint,
    properties_json text,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: product_analytics_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.product_analytics_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: product_analytics_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.product_analytics_events_id_seq OWNED BY public.product_analytics_events.id;


--
-- Name: provider_operations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.provider_operations (
    id bigint NOT NULL,
    operation_type character varying(20) NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    payment_id bigint,
    subscription_id bigint,
    amount numeric(12,2) NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(30) NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    provider_transaction_id character varying(255),
    provider_request_id character varying(255),
    provider_code character varying(80),
    provider_message character varying(500),
    provider_completed_at timestamp with time zone,
    local_applied_at timestamp with time zone,
    review_required_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_provider_operation_status CHECK (((status)::text = ANY ((ARRAY['INTENT'::character varying, 'PROVIDER_SUCCEEDED'::character varying, 'PROVIDER_FAILED'::character varying, 'REVIEW_REQUIRED'::character varying])::text[]))),
    CONSTRAINT ck_provider_operation_type CHECK (((operation_type)::text = ANY ((ARRAY['CHARGE'::character varying, 'REFUND'::character varying])::text[])))
);


--
-- Name: provider_operations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.provider_operations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: provider_operations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.provider_operations_id_seq OWNED BY public.provider_operations.id;


--
-- Name: push_subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.push_subscriptions (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    endpoint text NOT NULL,
    p256dh character varying(500),
    auth character varying(500),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: push_subscriptions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.push_subscriptions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: push_subscriptions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.push_subscriptions_id_seq OWNED BY public.push_subscriptions.id;


--
-- Name: refunds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refunds (
    id bigint NOT NULL,
    payment_id bigint NOT NULL,
    subscription_id bigint NOT NULL,
    status character varying(30) NOT NULL,
    provider_refund_id character varying(255),
    idempotency_key character varying(100) NOT NULL,
    currency character varying(3) DEFAULT 'TRY'::character varying NOT NULL,
    amount numeric(12,2) NOT NULL,
    reason character varying(500),
    failure_message character varying(500),
    refunded_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    payment_allocation_id bigint,
    attempt_count integer DEFAULT 0 NOT NULL,
    last_attempt_at timestamp with time zone,
    next_retry_at timestamp with time zone,
    CONSTRAINT chk_refund_attempt_count CHECK (((attempt_count >= 0) AND (attempt_count <= 3)))
);


--
-- Name: refunds_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.refunds_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: refunds_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.refunds_id_seq OWNED BY public.refunds.id;


--
-- Name: reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reviews (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    store_id bigint NOT NULL,
    subscription_id bigint NOT NULL,
    rating integer NOT NULL,
    comment text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    seller_reply text,
    seller_replied_at timestamp with time zone,
    CONSTRAINT reviews_rating_check CHECK (((rating >= 1) AND (rating <= 5)))
);


--
-- Name: reviews_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reviews_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reviews_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reviews_id_seq OWNED BY public.reviews.id;


--
-- Name: risk_cases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.risk_cases (
    id bigint NOT NULL,
    risk_type character varying(60) NOT NULL,
    severity character varying(20) NOT NULL,
    reference_type character varying(60) NOT NULL,
    reference_id bigint NOT NULL,
    summary character varying(500) NOT NULL,
    status character varying(30) DEFAULT 'OPEN'::character varying NOT NULL,
    assigned_admin_id bigint,
    resolution_note character varying(1000),
    resolved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: risk_cases_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.risk_cases_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: risk_cases_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.risk_cases_id_seq OWNED BY public.risk_cases.id;


--
-- Name: seller_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seller_documents (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    document_type character varying(50) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_url character varying(500) NOT NULL,
    expiry_date date,
    verified boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    verification_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    rejection_reason text,
    reviewed_at timestamp with time zone,
    reviewed_by bigint,
    file_size bigint,
    content_type character varying(100),
    storage_name character varying(255)
);


--
-- Name: seller_documents_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seller_documents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seller_documents_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.seller_documents_id_seq OWNED BY public.seller_documents.id;


--
-- Name: seller_payout_adjustments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seller_payout_adjustments (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    refund_id bigint NOT NULL,
    source_payout_id bigint NOT NULL,
    last_applied_payout_id bigint,
    amount numeric(12,2) NOT NULL,
    remaining_amount numeric(12,2) NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_payout_adjustment_amount CHECK ((amount > (0)::numeric)),
    CONSTRAINT ck_payout_adjustment_remaining CHECK (((remaining_amount >= (0)::numeric) AND (remaining_amount <= amount))),
    CONSTRAINT ck_payout_adjustment_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPLIED'::character varying])::text[])))
);


--
-- Name: seller_payout_adjustments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seller_payout_adjustments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seller_payout_adjustments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.seller_payout_adjustments_id_seq OWNED BY public.seller_payout_adjustments.id;


--
-- Name: seller_payout_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seller_payout_items (
    id bigint NOT NULL,
    payout_id bigint NOT NULL,
    payment_id bigint NOT NULL,
    refund_id bigint,
    item_type character varying(30) NOT NULL,
    gross_amount numeric(12,2) NOT NULL,
    commission_amount numeric(12,2) NOT NULL,
    net_amount numeric(12,2) NOT NULL,
    currency character varying(3) DEFAULT 'TRY'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: seller_payout_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seller_payout_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seller_payout_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.seller_payout_items_id_seq OWNED BY public.seller_payout_items.id;


--
-- Name: seller_payouts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seller_payouts (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    status character varying(30) NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    currency character varying(3) DEFAULT 'TRY'::character varying NOT NULL,
    gross_amount numeric(12,2) NOT NULL,
    commission_amount numeric(12,2) NOT NULL,
    refund_amount numeric(12,2) NOT NULL,
    net_amount numeric(12,2) NOT NULL,
    provider_payout_id character varying(255),
    scheduled_at timestamp with time zone,
    paid_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    adjustment_amount numeric(12,2) DEFAULT 0 NOT NULL
);


--
-- Name: seller_payouts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seller_payouts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seller_payouts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.seller_payouts_id_seq OWNED BY public.seller_payouts.id;


--
-- Name: seller_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seller_profiles (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    company_title character varying(255) NOT NULL,
    tax_number character varying(20) NOT NULL,
    tax_office character varying(100) NOT NULL,
    authorized_person character varying(200) NOT NULL,
    phone character varying(20),
    bank_name character varying(100),
    iban character varying(34),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: seller_profiles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seller_profiles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seller_profiles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.seller_profiles_id_seq OWNED BY public.seller_profiles.id;


--
-- Name: seller_sla_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seller_sla_events (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    subscription_id bigint,
    event_type character varying(60) NOT NULL,
    reason character varying(500) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0,
    deleted_at timestamp with time zone
);


--
-- Name: seller_sla_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seller_sla_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seller_sla_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.seller_sla_events_id_seq OWNED BY public.seller_sla_events.id;


--
-- Name: service_areas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_areas (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    city character varying(50) NOT NULL,
    district character varying(50) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: service_areas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.service_areas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: service_areas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.service_areas_id_seq OWNED BY public.service_areas.id;


--
-- Name: service_demands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_demands (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    address_id bigint NOT NULL,
    city character varying(255) NOT NULL,
    district character varying(255) NOT NULL,
    neighborhood character varying(255),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint,
    deleted_at timestamp with time zone
);


--
-- Name: service_demands_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.service_demands_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: service_demands_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.service_demands_id_seq OWNED BY public.service_demands.id;


--
-- Name: store_capacity_overrides; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_capacity_overrides (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    capacity_date date NOT NULL,
    capacity integer NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint,
    deleted_at timestamp with time zone,
    CONSTRAINT store_capacity_overrides_capacity_check CHECK ((capacity >= 0))
);


--
-- Name: store_capacity_overrides_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.store_capacity_overrides_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: store_capacity_overrides_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.store_capacity_overrides_id_seq OWNED BY public.store_capacity_overrides.id;


--
-- Name: store_category_labels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_category_labels (
    store_id bigint NOT NULL,
    category character varying(60) NOT NULL
);


--
-- Name: store_closed_dates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_closed_dates (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    closed_date date NOT NULL,
    reason character varying(200),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: store_closed_dates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.store_closed_dates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: store_closed_dates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.store_closed_dates_id_seq OWNED BY public.store_closed_dates.id;


--
-- Name: store_delivery_slots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_delivery_slots (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    delivery_time time without time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: store_delivery_slots_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.store_delivery_slots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: store_delivery_slots_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.store_delivery_slots_id_seq OWNED BY public.store_delivery_slots.id;


--
-- Name: store_distance_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_distance_rules (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    distance_km integer NOT NULL,
    min_person_count integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: store_distance_rules_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.store_distance_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: store_distance_rules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.store_distance_rules_id_seq OWNED BY public.store_distance_rules.id;


--
-- Name: store_onboarding; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_onboarding (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    contract_version character varying(40),
    contract_accepted_at timestamp with time zone,
    submitted_at timestamp with time zone,
    approved_at timestamp with time zone,
    rejected_at timestamp with time zone,
    rejection_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: store_onboarding_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.store_onboarding_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: store_onboarding_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.store_onboarding_id_seq OWNED BY public.store_onboarding.id;


--
-- Name: store_staff; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_staff (
    id bigint NOT NULL,
    store_id bigint NOT NULL,
    user_id bigint,
    email character varying(255) NOT NULL,
    staff_role character varying(30) NOT NULL,
    status character varying(20) DEFAULT 'INVITED'::character varying NOT NULL,
    invitation_token_hash character varying(128),
    invitation_expires_at timestamp with time zone,
    accepted_at timestamp with time zone,
    deactivated_at timestamp with time zone,
    invited_by bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: store_staff_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.store_staff_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: store_staff_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.store_staff_id_seq OWNED BY public.store_staff.id;


--
-- Name: store_views; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_views (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    store_id bigint NOT NULL,
    viewed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: store_views_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.store_views_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: store_views_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.store_views_id_seq OWNED BY public.store_views.id;


--
-- Name: stores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stores (
    id bigint NOT NULL,
    seller_id bigint NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    logo_url character varying(500),
    cover_image_url character varying(500),
    min_person_count integer,
    max_person_count integer,
    daily_capacity integer,
    production_address character varying(500),
    status character varying(30) DEFAULT 'DRAFT'::character varying NOT NULL,
    rating numeric(3,1) DEFAULT 0.0 NOT NULL,
    review_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    temporarily_closed boolean DEFAULT false NOT NULL,
    latitude numeric(10,7) NOT NULL,
    longitude numeric(10,7) NOT NULL,
    address_title character varying(100),
    city character varying(100),
    district character varying(100),
    neighborhood character varying(150),
    street character varying(200),
    building_no character varying(50),
    floor character varying(50),
    apartment_no character varying(50),
    directions text,
    change_cutoff_hours integer DEFAULT 24 NOT NULL
);


--
-- Name: stores_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stores_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stores_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stores_id_seq OWNED BY public.stores.id;


--
-- Name: subscription_adjustments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_adjustments (
    id bigint NOT NULL,
    subscription_id bigint NOT NULL,
    delivery_id bigint,
    adjustment_type character varying(30) NOT NULL,
    status character varying(30) NOT NULL,
    amount numeric(12,2) NOT NULL,
    currency character varying(3) DEFAULT 'TRY'::character varying NOT NULL,
    refund_id bigint,
    reason character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: subscription_adjustments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.subscription_adjustments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subscription_adjustments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.subscription_adjustments_id_seq OWNED BY public.subscription_adjustments.id;


--
-- Name: subscription_deliveries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_deliveries (
    id bigint NOT NULL,
    subscription_id bigint NOT NULL,
    delivery_date date NOT NULL,
    delivery_time time without time zone NOT NULL,
    person_count integer NOT NULL,
    menu_id bigint NOT NULL,
    address_id bigint NOT NULL,
    status character varying(20) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    notes text,
    delivered_at timestamp with time zone,
    delivered_by_user_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    change_reason character varying(500),
    changed_at timestamp with time zone,
    changed_by_user_id bigint,
    status_changed_at timestamp with time zone,
    preparation_started_at timestamp with time zone,
    in_transit_at timestamp with time zone,
    estimated_delivery_at timestamp with time zone,
    delivery_attempted_at timestamp with time zone,
    failure_reason character varying(500),
    receiver_name character varying(150),
    proof_photo_url character varying(500),
    delivery_code character varying(6),
    delay_minutes integer,
    courier_latitude numeric(10,7),
    courier_longitude numeric(10,7),
    courier_id bigint,
    route_sequence integer,
    delivery_type character varying(30) DEFAULT 'STORE_COURIER'::character varying NOT NULL,
    compensation_status character varying(30),
    suggested_compensation_date date,
    makeup_source_delivery_id bigint,
    customer_note character varying(500)
);


--
-- Name: subscription_deliveries_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.subscription_deliveries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subscription_deliveries_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.subscription_deliveries_id_seq OWNED BY public.subscription_deliveries.id;


--
-- Name: subscription_extension_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_extension_requests (
    id bigint NOT NULL,
    subscription_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    old_end_date date NOT NULL,
    new_end_date date NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    decision_reason character varying(500),
    decided_at timestamp with time zone,
    decided_by_user_id bigint,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT chk_extension_date_range CHECK ((new_end_date > old_end_date)),
    CONSTRAINT chk_extension_request_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: subscription_extension_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.subscription_extension_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subscription_extension_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.subscription_extension_requests_id_seq OWNED BY public.subscription_extension_requests.id;


--
-- Name: subscription_freezes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_freezes (
    id bigint NOT NULL,
    subscription_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    reason character varying(500),
    affected_delivery_count integer NOT NULL,
    adjustment_amount numeric(12,2) NOT NULL,
    currency character varying(3) DEFAULT 'TRY'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: subscription_freezes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.subscription_freezes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subscription_freezes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.subscription_freezes_id_seq OWNED BY public.subscription_freezes.id;


--
-- Name: subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscriptions (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    store_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    address_id bigint NOT NULL,
    person_count integer NOT NULL,
    price_per_person numeric(10,2) NOT NULL,
    delivery_time time without time zone NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    service_day_count integer NOT NULL,
    total_amount numeric(12,2) NOT NULL,
    status character varying(30) DEFAULT 'PENDING_APPROVAL'::character varying NOT NULL,
    approved_at timestamp with time zone,
    rejected_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    completed_at timestamp with time zone,
    cancellation_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    idempotency_key character varying(100),
    payment_method_id bigint,
    commercial_terms_accepted_at timestamp with time zone,
    approval_deadline_at timestamp with time zone,
    seller_viewed_at timestamp with time zone,
    menu_version_id bigint,
    menu_name_snapshot character varying(255),
    menu_schedule_snapshot_json text,
    campaign_id bigint,
    coupon_code character varying(50),
    discount_amount numeric(12,2) DEFAULT 0 NOT NULL,
    auto_renew boolean DEFAULT false NOT NULL,
    renewal_period_days integer DEFAULT 28 NOT NULL,
    renewal_price_notice_for_end_date date,
    last_auto_renewed_at timestamp with time zone,
    payment_token_consent_at timestamp with time zone
);


--
-- Name: subscriptions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.subscriptions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subscriptions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.subscriptions_id_seq OWNED BY public.subscriptions.id;


--
-- Name: user_data_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_data_requests (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    request_type character varying(30) NOT NULL,
    status character varying(30) NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: user_data_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_data_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_data_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_data_requests_id_seq OWNED BY public.user_data_requests.id;


--
-- Name: user_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_sessions (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    refresh_token_hash character varying(64) NOT NULL,
    device_name character varying(200),
    ip_address character varying(64),
    last_seen_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: user_sessions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_sessions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_sessions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_sessions_id_seq OWNED BY public.user_sessions.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    phone character varying(20),
    role character varying(20) NOT NULL,
    email_verified boolean DEFAULT false NOT NULL,
    email_verified_at timestamp with time zone,
    terms_accepted_at timestamp with time zone,
    terms_version character varying(20),
    privacy_accepted_at timestamp with time zone,
    privacy_version character varying(20),
    account_deleted_at timestamp with time zone,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    phone_verified boolean DEFAULT false NOT NULL,
    phone_verified_at timestamp with time zone,
    referral_code character varying(32)
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: verification_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.verification_tokens (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    token_type character varying(40) NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    attempts integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: verification_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.verification_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: verification_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.verification_tokens_id_seq OWNED BY public.verification_tokens.id;


--
-- Name: webhook_subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.webhook_subscriptions (
    id bigint NOT NULL,
    target_url character varying(1000) NOT NULL,
    secret_hash character varying(255) NOT NULL,
    event_types character varying(1000) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    failure_count integer DEFAULT 0 NOT NULL,
    last_attempt_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: webhook_subscriptions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.webhook_subscriptions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: webhook_subscriptions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.webhook_subscriptions_id_seq OWNED BY public.webhook_subscriptions.id;


--
-- Name: addresses id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.addresses ALTER COLUMN id SET DEFAULT nextval('public.addresses_id_seq'::regclass);


--
-- Name: audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);


--
-- Name: automation_tasks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.automation_tasks ALTER COLUMN id SET DEFAULT nextval('public.automation_tasks_id_seq'::regclass);


--
-- Name: bank_catalog id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_catalog ALTER COLUMN id SET DEFAULT nextval('public.bank_catalog_id_seq'::regclass);


--
-- Name: business_hours id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_hours ALTER COLUMN id SET DEFAULT nextval('public.business_hours_id_seq'::regclass);


--
-- Name: campaign_redemptions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_redemptions ALTER COLUMN id SET DEFAULT nextval('public.campaign_redemptions_id_seq'::regclass);


--
-- Name: campaigns id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaigns ALTER COLUMN id SET DEFAULT nextval('public.campaigns_id_seq'::regclass);


--
-- Name: commission_rules id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commission_rules ALTER COLUMN id SET DEFAULT nextval('public.commission_rules_id_seq'::regclass);


--
-- Name: complaint_attachments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.complaint_attachments ALTER COLUMN id SET DEFAULT nextval('public.complaint_attachments_id_seq'::regclass);


--
-- Name: complaints id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.complaints ALTER COLUMN id SET DEFAULT nextval('public.complaints_id_seq'::regclass);


--
-- Name: consent_records id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consent_records ALTER COLUMN id SET DEFAULT nextval('public.consent_records_id_seq'::regclass);


--
-- Name: couriers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.couriers ALTER COLUMN id SET DEFAULT nextval('public.couriers_id_seq'::regclass);


--
-- Name: customer_profiles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_profiles ALTER COLUMN id SET DEFAULT nextval('public.customer_profiles_id_seq'::regclass);


--
-- Name: delivery_modification_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history ALTER COLUMN id SET DEFAULT nextval('public.delivery_modification_history_id_seq'::regclass);


--
-- Name: delivery_proofs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_proofs ALTER COLUMN id SET DEFAULT nextval('public.delivery_proofs_id_seq'::regclass);


--
-- Name: favorites id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.favorites ALTER COLUMN id SET DEFAULT nextval('public.favorites_id_seq'::regclass);


--
-- Name: feature_flags id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feature_flags ALTER COLUMN id SET DEFAULT nextval('public.feature_flags_id_seq'::regclass);


--
-- Name: finance_reconciliations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_reconciliations ALTER COLUMN id SET DEFAULT nextval('public.finance_reconciliations_id_seq'::regclass);


--
-- Name: integration_api_keys id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_api_keys ALTER COLUMN id SET DEFAULT nextval('public.integration_api_keys_id_seq'::regclass);


--
-- Name: invoices id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices ALTER COLUMN id SET DEFAULT nextval('public.invoices_id_seq'::regclass);


--
-- Name: meal_balance_accounts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_balance_accounts ALTER COLUMN id SET DEFAULT nextval('public.meal_balance_accounts_id_seq'::regclass);


--
-- Name: meal_balance_transactions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_balance_transactions ALTER COLUMN id SET DEFAULT nextval('public.meal_balance_transactions_id_seq'::regclass);


--
-- Name: menu_gallery_images id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_gallery_images ALTER COLUMN id SET DEFAULT nextval('public.menu_gallery_images_id_seq'::regclass);


--
-- Name: menu_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_items ALTER COLUMN id SET DEFAULT nextval('public.menu_items_id_seq'::regclass);


--
-- Name: menu_schedule_versions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_schedule_versions ALTER COLUMN id SET DEFAULT nextval('public.menu_schedule_versions_id_seq'::regclass);


--
-- Name: menu_schedules id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_schedules ALTER COLUMN id SET DEFAULT nextval('public.menu_schedules_id_seq'::regclass);


--
-- Name: menu_versions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_versions ALTER COLUMN id SET DEFAULT nextval('public.menu_versions_id_seq'::regclass);


--
-- Name: menus id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menus ALTER COLUMN id SET DEFAULT nextval('public.menus_id_seq'::regclass);


--
-- Name: notification_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_events ALTER COLUMN id SET DEFAULT nextval('public.notification_events_id_seq'::regclass);


--
-- Name: notification_preferences id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences ALTER COLUMN id SET DEFAULT nextval('public.notification_preferences_id_seq'::regclass);


--
-- Name: notification_templates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates ALTER COLUMN id SET DEFAULT nextval('public.notification_templates_id_seq'::regclass);


--
-- Name: notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications ALTER COLUMN id SET DEFAULT nextval('public.notifications_id_seq'::regclass);


--
-- Name: payment_allocations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_allocations ALTER COLUMN id SET DEFAULT nextval('public.payment_allocations_id_seq'::regclass);


--
-- Name: payment_attempts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_attempts ALTER COLUMN id SET DEFAULT nextval('public.payment_attempts_id_seq'::regclass);


--
-- Name: payment_card_management_sessions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_card_management_sessions ALTER COLUMN id SET DEFAULT nextval('public.payment_card_management_sessions_id_seq'::regclass);


--
-- Name: payment_checkout_sessions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_checkout_sessions ALTER COLUMN id SET DEFAULT nextval('public.payment_checkout_sessions_id_seq'::regclass);


--
-- Name: payment_methods id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_methods ALTER COLUMN id SET DEFAULT nextval('public.payment_methods_id_seq'::regclass);


--
-- Name: payment_webhook_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_webhook_events ALTER COLUMN id SET DEFAULT nextval('public.payment_webhook_events_id_seq'::regclass);


--
-- Name: payments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments ALTER COLUMN id SET DEFAULT nextval('public.payments_id_seq'::regclass);


--
-- Name: platform_settings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_settings ALTER COLUMN id SET DEFAULT nextval('public.platform_settings_id_seq'::regclass);


--
-- Name: product_analytics_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_analytics_events ALTER COLUMN id SET DEFAULT nextval('public.product_analytics_events_id_seq'::regclass);


--
-- Name: provider_operations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.provider_operations ALTER COLUMN id SET DEFAULT nextval('public.provider_operations_id_seq'::regclass);


--
-- Name: push_subscriptions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.push_subscriptions ALTER COLUMN id SET DEFAULT nextval('public.push_subscriptions_id_seq'::regclass);


--
-- Name: refunds id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refunds ALTER COLUMN id SET DEFAULT nextval('public.refunds_id_seq'::regclass);


--
-- Name: reviews id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews ALTER COLUMN id SET DEFAULT nextval('public.reviews_id_seq'::regclass);


--
-- Name: risk_cases id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_cases ALTER COLUMN id SET DEFAULT nextval('public.risk_cases_id_seq'::regclass);


--
-- Name: seller_documents id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_documents ALTER COLUMN id SET DEFAULT nextval('public.seller_documents_id_seq'::regclass);


--
-- Name: seller_payout_adjustments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_adjustments ALTER COLUMN id SET DEFAULT nextval('public.seller_payout_adjustments_id_seq'::regclass);


--
-- Name: seller_payout_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_items ALTER COLUMN id SET DEFAULT nextval('public.seller_payout_items_id_seq'::regclass);


--
-- Name: seller_payouts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payouts ALTER COLUMN id SET DEFAULT nextval('public.seller_payouts_id_seq'::regclass);


--
-- Name: seller_profiles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_profiles ALTER COLUMN id SET DEFAULT nextval('public.seller_profiles_id_seq'::regclass);


--
-- Name: seller_sla_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_sla_events ALTER COLUMN id SET DEFAULT nextval('public.seller_sla_events_id_seq'::regclass);


--
-- Name: service_areas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_areas ALTER COLUMN id SET DEFAULT nextval('public.service_areas_id_seq'::regclass);


--
-- Name: service_demands id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_demands ALTER COLUMN id SET DEFAULT nextval('public.service_demands_id_seq'::regclass);


--
-- Name: store_capacity_overrides id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_capacity_overrides ALTER COLUMN id SET DEFAULT nextval('public.store_capacity_overrides_id_seq'::regclass);


--
-- Name: store_closed_dates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_closed_dates ALTER COLUMN id SET DEFAULT nextval('public.store_closed_dates_id_seq'::regclass);


--
-- Name: store_delivery_slots id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_delivery_slots ALTER COLUMN id SET DEFAULT nextval('public.store_delivery_slots_id_seq'::regclass);


--
-- Name: store_distance_rules id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_distance_rules ALTER COLUMN id SET DEFAULT nextval('public.store_distance_rules_id_seq'::regclass);


--
-- Name: store_onboarding id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_onboarding ALTER COLUMN id SET DEFAULT nextval('public.store_onboarding_id_seq'::regclass);


--
-- Name: store_staff id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_staff ALTER COLUMN id SET DEFAULT nextval('public.store_staff_id_seq'::regclass);


--
-- Name: store_views id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_views ALTER COLUMN id SET DEFAULT nextval('public.store_views_id_seq'::regclass);


--
-- Name: stores id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stores ALTER COLUMN id SET DEFAULT nextval('public.stores_id_seq'::regclass);


--
-- Name: subscription_adjustments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_adjustments ALTER COLUMN id SET DEFAULT nextval('public.subscription_adjustments_id_seq'::regclass);


--
-- Name: subscription_deliveries id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_deliveries ALTER COLUMN id SET DEFAULT nextval('public.subscription_deliveries_id_seq'::regclass);


--
-- Name: subscription_extension_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_extension_requests ALTER COLUMN id SET DEFAULT nextval('public.subscription_extension_requests_id_seq'::regclass);


--
-- Name: subscription_freezes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_freezes ALTER COLUMN id SET DEFAULT nextval('public.subscription_freezes_id_seq'::regclass);


--
-- Name: subscriptions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions ALTER COLUMN id SET DEFAULT nextval('public.subscriptions_id_seq'::regclass);


--
-- Name: user_data_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_data_requests ALTER COLUMN id SET DEFAULT nextval('public.user_data_requests_id_seq'::regclass);


--
-- Name: user_sessions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_sessions ALTER COLUMN id SET DEFAULT nextval('public.user_sessions_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: verification_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verification_tokens ALTER COLUMN id SET DEFAULT nextval('public.verification_tokens_id_seq'::regclass);


--
-- Name: webhook_subscriptions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.webhook_subscriptions ALTER COLUMN id SET DEFAULT nextval('public.webhook_subscriptions_id_seq'::regclass);


--
-- Name: addresses addresses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.addresses
    ADD CONSTRAINT addresses_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: automation_tasks automation_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.automation_tasks
    ADD CONSTRAINT automation_tasks_pkey PRIMARY KEY (id);


--
-- Name: bank_catalog bank_catalog_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_catalog
    ADD CONSTRAINT bank_catalog_name_key UNIQUE (name);


--
-- Name: bank_catalog bank_catalog_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_catalog
    ADD CONSTRAINT bank_catalog_pkey PRIMARY KEY (id);


--
-- Name: business_hours business_hours_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_hours
    ADD CONSTRAINT business_hours_pkey PRIMARY KEY (id);


--
-- Name: business_hours business_hours_store_id_day_of_week_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_hours
    ADD CONSTRAINT business_hours_store_id_day_of_week_key UNIQUE (store_id, day_of_week);


--
-- Name: campaign_redemptions campaign_redemptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_redemptions
    ADD CONSTRAINT campaign_redemptions_pkey PRIMARY KEY (id);


--
-- Name: campaigns campaigns_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaigns
    ADD CONSTRAINT campaigns_code_key UNIQUE (code);


--
-- Name: campaigns campaigns_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaigns
    ADD CONSTRAINT campaigns_pkey PRIMARY KEY (id);


--
-- Name: commission_rules commission_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commission_rules
    ADD CONSTRAINT commission_rules_pkey PRIMARY KEY (id);


--
-- Name: complaint_attachments complaint_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.complaint_attachments
    ADD CONSTRAINT complaint_attachments_pkey PRIMARY KEY (id);


--
-- Name: complaints complaints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.complaints
    ADD CONSTRAINT complaints_pkey PRIMARY KEY (id);


--
-- Name: consent_records consent_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consent_records
    ADD CONSTRAINT consent_records_pkey PRIMARY KEY (id);


--
-- Name: couriers couriers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.couriers
    ADD CONSTRAINT couriers_pkey PRIMARY KEY (id);


--
-- Name: customer_profiles customer_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_profiles
    ADD CONSTRAINT customer_profiles_pkey PRIMARY KEY (id);


--
-- Name: customer_profiles customer_profiles_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_profiles
    ADD CONSTRAINT customer_profiles_user_id_key UNIQUE (user_id);


--
-- Name: delivery_modification_history delivery_modification_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_pkey PRIMARY KEY (id);


--
-- Name: delivery_proofs delivery_proofs_delivery_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_proofs
    ADD CONSTRAINT delivery_proofs_delivery_id_key UNIQUE (delivery_id);


--
-- Name: delivery_proofs delivery_proofs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_proofs
    ADD CONSTRAINT delivery_proofs_pkey PRIMARY KEY (id);


--
-- Name: favorites favorites_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.favorites
    ADD CONSTRAINT favorites_pkey PRIMARY KEY (id);


--
-- Name: favorites favorites_user_id_store_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.favorites
    ADD CONSTRAINT favorites_user_id_store_id_key UNIQUE (user_id, store_id);


--
-- Name: feature_flags feature_flags_flag_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feature_flags
    ADD CONSTRAINT feature_flags_flag_key_key UNIQUE (flag_key);


--
-- Name: feature_flags feature_flags_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feature_flags
    ADD CONSTRAINT feature_flags_pkey PRIMARY KEY (id);


--
-- Name: finance_reconciliations finance_reconciliations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_reconciliations
    ADD CONSTRAINT finance_reconciliations_pkey PRIMARY KEY (id);


--
-- Name: finance_reconciliations finance_reconciliations_reconciliation_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_reconciliations
    ADD CONSTRAINT finance_reconciliations_reconciliation_date_key UNIQUE (reconciliation_date);


--
-- Name: integration_api_keys integration_api_keys_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_api_keys
    ADD CONSTRAINT integration_api_keys_pkey PRIMARY KEY (id);


--
-- Name: invoices invoices_invoice_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_invoice_number_key UNIQUE (invoice_number);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);


--
-- Name: meal_balance_accounts meal_balance_accounts_customer_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_balance_accounts
    ADD CONSTRAINT meal_balance_accounts_customer_id_key UNIQUE (customer_id);


--
-- Name: meal_balance_accounts meal_balance_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_balance_accounts
    ADD CONSTRAINT meal_balance_accounts_pkey PRIMARY KEY (id);


--
-- Name: meal_balance_transactions meal_balance_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_balance_transactions
    ADD CONSTRAINT meal_balance_transactions_pkey PRIMARY KEY (id);


--
-- Name: meal_balance_transactions meal_balance_transactions_reference_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_balance_transactions
    ADD CONSTRAINT meal_balance_transactions_reference_key_key UNIQUE (reference_key);


--
-- Name: menu_allergens menu_allergens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_allergens
    ADD CONSTRAINT menu_allergens_pkey PRIMARY KEY (menu_id, allergen);


--
-- Name: menu_diet_tags menu_diet_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_diet_tags
    ADD CONSTRAINT menu_diet_tags_pkey PRIMARY KEY (menu_id, diet_tag);


--
-- Name: menu_gallery_images menu_gallery_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_gallery_images
    ADD CONSTRAINT menu_gallery_images_pkey PRIMARY KEY (id);


--
-- Name: menu_items menu_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_items
    ADD CONSTRAINT menu_items_pkey PRIMARY KEY (id);


--
-- Name: menu_schedule_versions menu_schedule_versions_menu_version_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_schedule_versions
    ADD CONSTRAINT menu_schedule_versions_menu_version_id_key UNIQUE (menu_version_id);


--
-- Name: menu_schedule_versions menu_schedule_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_schedule_versions
    ADD CONSTRAINT menu_schedule_versions_pkey PRIMARY KEY (id);


--
-- Name: menu_schedules menu_schedules_menu_id_day_of_week_sort_order_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_schedules
    ADD CONSTRAINT menu_schedules_menu_id_day_of_week_sort_order_key UNIQUE (menu_id, day_of_week, sort_order);


--
-- Name: menu_schedules menu_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_schedules
    ADD CONSTRAINT menu_schedules_pkey PRIMARY KEY (id);


--
-- Name: menu_versions menu_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_versions
    ADD CONSTRAINT menu_versions_pkey PRIMARY KEY (id);


--
-- Name: menus menus_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menus
    ADD CONSTRAINT menus_pkey PRIMARY KEY (id);


--
-- Name: notification_events notification_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_events
    ADD CONSTRAINT notification_events_pkey PRIMARY KEY (id);


--
-- Name: notification_preferences notification_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT notification_preferences_pkey PRIMARY KEY (id);


--
-- Name: notification_preferences notification_preferences_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT notification_preferences_user_id_key UNIQUE (user_id);


--
-- Name: notification_templates notification_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates
    ADD CONSTRAINT notification_templates_pkey PRIMARY KEY (id);


--
-- Name: notification_templates notification_templates_template_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates
    ADD CONSTRAINT notification_templates_template_code_key UNIQUE (template_code);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: payment_allocations payment_allocations_payment_id_delivery_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_allocations
    ADD CONSTRAINT payment_allocations_payment_id_delivery_id_key UNIQUE (payment_id, delivery_id);


--
-- Name: payment_allocations payment_allocations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_allocations
    ADD CONSTRAINT payment_allocations_pkey PRIMARY KEY (id);


--
-- Name: payment_attempts payment_attempts_payment_id_attempt_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_attempts
    ADD CONSTRAINT payment_attempts_payment_id_attempt_number_key UNIQUE (payment_id, attempt_number);


--
-- Name: payment_attempts payment_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_attempts
    ADD CONSTRAINT payment_attempts_pkey PRIMARY KEY (id);


--
-- Name: payment_card_management_sessions payment_card_management_sessions_conversation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_card_management_sessions
    ADD CONSTRAINT payment_card_management_sessions_conversation_id_key UNIQUE (conversation_id);


--
-- Name: payment_card_management_sessions payment_card_management_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_card_management_sessions
    ADD CONSTRAINT payment_card_management_sessions_pkey PRIMARY KEY (id);


--
-- Name: payment_card_management_sessions payment_card_management_sessions_provider_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_card_management_sessions
    ADD CONSTRAINT payment_card_management_sessions_provider_token_key UNIQUE (provider_token);


--
-- Name: payment_checkout_sessions payment_checkout_sessions_conversation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_checkout_sessions
    ADD CONSTRAINT payment_checkout_sessions_conversation_id_key UNIQUE (conversation_id);


--
-- Name: payment_checkout_sessions payment_checkout_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_checkout_sessions
    ADD CONSTRAINT payment_checkout_sessions_pkey PRIMARY KEY (id);


--
-- Name: payment_checkout_sessions payment_checkout_sessions_provider_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_checkout_sessions
    ADD CONSTRAINT payment_checkout_sessions_provider_token_key UNIQUE (provider_token);


--
-- Name: payment_methods payment_methods_customer_id_provider_provider_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_methods
    ADD CONSTRAINT payment_methods_customer_id_provider_provider_token_key UNIQUE (customer_id, provider, provider_token);


--
-- Name: payment_methods payment_methods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_methods
    ADD CONSTRAINT payment_methods_pkey PRIMARY KEY (id);


--
-- Name: payment_webhook_events payment_webhook_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_webhook_events
    ADD CONSTRAINT payment_webhook_events_pkey PRIMARY KEY (id);


--
-- Name: payment_webhook_events payment_webhook_events_provider_provider_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_webhook_events
    ADD CONSTRAINT payment_webhook_events_provider_provider_event_id_key UNIQUE (provider, provider_event_id);


--
-- Name: payments payments_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: platform_settings platform_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_settings
    ADD CONSTRAINT platform_settings_pkey PRIMARY KEY (id);


--
-- Name: platform_settings platform_settings_setting_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_settings
    ADD CONSTRAINT platform_settings_setting_key_key UNIQUE (setting_key);


--
-- Name: product_analytics_events product_analytics_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_analytics_events
    ADD CONSTRAINT product_analytics_events_pkey PRIMARY KEY (id);


--
-- Name: provider_operations provider_operations_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.provider_operations
    ADD CONSTRAINT provider_operations_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: provider_operations provider_operations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.provider_operations
    ADD CONSTRAINT provider_operations_pkey PRIMARY KEY (id);


--
-- Name: push_subscriptions push_subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.push_subscriptions
    ADD CONSTRAINT push_subscriptions_pkey PRIMARY KEY (id);


--
-- Name: push_subscriptions push_subscriptions_user_id_endpoint_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.push_subscriptions
    ADD CONSTRAINT push_subscriptions_user_id_endpoint_key UNIQUE (user_id, endpoint);


--
-- Name: refunds refunds_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT refunds_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: refunds refunds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT refunds_pkey PRIMARY KEY (id);


--
-- Name: reviews reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_pkey PRIMARY KEY (id);


--
-- Name: risk_cases risk_cases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_cases
    ADD CONSTRAINT risk_cases_pkey PRIMARY KEY (id);


--
-- Name: risk_cases risk_cases_risk_type_reference_type_reference_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_cases
    ADD CONSTRAINT risk_cases_risk_type_reference_type_reference_id_key UNIQUE (risk_type, reference_type, reference_id);


--
-- Name: seller_documents seller_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_documents
    ADD CONSTRAINT seller_documents_pkey PRIMARY KEY (id);


--
-- Name: seller_payout_adjustments seller_payout_adjustments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_adjustments
    ADD CONSTRAINT seller_payout_adjustments_pkey PRIMARY KEY (id);


--
-- Name: seller_payout_adjustments seller_payout_adjustments_refund_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_adjustments
    ADD CONSTRAINT seller_payout_adjustments_refund_id_key UNIQUE (refund_id);


--
-- Name: seller_payout_items seller_payout_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_items
    ADD CONSTRAINT seller_payout_items_pkey PRIMARY KEY (id);


--
-- Name: seller_payouts seller_payouts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payouts
    ADD CONSTRAINT seller_payouts_pkey PRIMARY KEY (id);


--
-- Name: seller_payouts seller_payouts_store_id_period_start_period_end_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payouts
    ADD CONSTRAINT seller_payouts_store_id_period_start_period_end_key UNIQUE (store_id, period_start, period_end);


--
-- Name: seller_profiles seller_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_profiles
    ADD CONSTRAINT seller_profiles_pkey PRIMARY KEY (id);


--
-- Name: seller_profiles seller_profiles_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_profiles
    ADD CONSTRAINT seller_profiles_user_id_key UNIQUE (user_id);


--
-- Name: seller_sla_events seller_sla_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_sla_events
    ADD CONSTRAINT seller_sla_events_pkey PRIMARY KEY (id);


--
-- Name: service_areas service_areas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_areas
    ADD CONSTRAINT service_areas_pkey PRIMARY KEY (id);


--
-- Name: service_areas service_areas_store_id_city_district_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_areas
    ADD CONSTRAINT service_areas_store_id_city_district_key UNIQUE (store_id, city, district);


--
-- Name: service_demands service_demands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_demands
    ADD CONSTRAINT service_demands_pkey PRIMARY KEY (id);


--
-- Name: store_capacity_overrides store_capacity_overrides_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_capacity_overrides
    ADD CONSTRAINT store_capacity_overrides_pkey PRIMARY KEY (id);


--
-- Name: store_category_labels store_category_labels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_category_labels
    ADD CONSTRAINT store_category_labels_pkey PRIMARY KEY (store_id, category);


--
-- Name: store_closed_dates store_closed_dates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_closed_dates
    ADD CONSTRAINT store_closed_dates_pkey PRIMARY KEY (id);


--
-- Name: store_closed_dates store_closed_dates_store_id_closed_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_closed_dates
    ADD CONSTRAINT store_closed_dates_store_id_closed_date_key UNIQUE (store_id, closed_date);


--
-- Name: store_delivery_slots store_delivery_slots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_delivery_slots
    ADD CONSTRAINT store_delivery_slots_pkey PRIMARY KEY (id);


--
-- Name: store_distance_rules store_distance_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_distance_rules
    ADD CONSTRAINT store_distance_rules_pkey PRIMARY KEY (id);


--
-- Name: store_distance_rules store_distance_rules_store_id_distance_km_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_distance_rules
    ADD CONSTRAINT store_distance_rules_store_id_distance_km_key UNIQUE (store_id, distance_km);


--
-- Name: store_onboarding store_onboarding_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_onboarding
    ADD CONSTRAINT store_onboarding_pkey PRIMARY KEY (id);


--
-- Name: store_onboarding store_onboarding_store_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_onboarding
    ADD CONSTRAINT store_onboarding_store_id_key UNIQUE (store_id);


--
-- Name: store_staff store_staff_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_staff
    ADD CONSTRAINT store_staff_pkey PRIMARY KEY (id);


--
-- Name: store_staff store_staff_store_id_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_staff
    ADD CONSTRAINT store_staff_store_id_email_key UNIQUE (store_id, email);


--
-- Name: store_views store_views_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_views
    ADD CONSTRAINT store_views_pkey PRIMARY KEY (id);


--
-- Name: stores stores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stores
    ADD CONSTRAINT stores_pkey PRIMARY KEY (id);


--
-- Name: subscription_adjustments subscription_adjustments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_adjustments
    ADD CONSTRAINT subscription_adjustments_pkey PRIMARY KEY (id);


--
-- Name: subscription_deliveries subscription_deliveries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_deliveries
    ADD CONSTRAINT subscription_deliveries_pkey PRIMARY KEY (id);


--
-- Name: subscription_deliveries subscription_deliveries_subscription_id_delivery_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_deliveries
    ADD CONSTRAINT subscription_deliveries_subscription_id_delivery_date_key UNIQUE (subscription_id, delivery_date);


--
-- Name: subscription_extension_requests subscription_extension_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_extension_requests
    ADD CONSTRAINT subscription_extension_requests_pkey PRIMARY KEY (id);


--
-- Name: subscription_freezes subscription_freezes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_freezes
    ADD CONSTRAINT subscription_freezes_pkey PRIMARY KEY (id);


--
-- Name: subscriptions subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);


--
-- Name: subscription_deliveries uk_delivery_makeup_source; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_deliveries
    ADD CONSTRAINT uk_delivery_makeup_source UNIQUE (makeup_source_delivery_id);


--
-- Name: menu_versions uk_menu_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_versions
    ADD CONSTRAINT uk_menu_version UNIQUE (menu_id, version_number);


--
-- Name: service_demands uk_service_demand_user_address; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_demands
    ADD CONSTRAINT uk_service_demand_user_address UNIQUE (user_id, address_id);


--
-- Name: store_capacity_overrides uk_store_capacity_override_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_capacity_overrides
    ADD CONSTRAINT uk_store_capacity_override_date UNIQUE (store_id, capacity_date);


--
-- Name: store_delivery_slots uk_store_delivery_slots_store_time; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_delivery_slots
    ADD CONSTRAINT uk_store_delivery_slots_store_time UNIQUE (store_id, delivery_time);


--
-- Name: store_views uk_store_views_user_store; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_views
    ADD CONSTRAINT uk_store_views_user_store UNIQUE (user_id, store_id);


--
-- Name: user_data_requests user_data_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_data_requests
    ADD CONSTRAINT user_data_requests_pkey PRIMARY KEY (id);


--
-- Name: user_sessions user_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT user_sessions_pkey PRIMARY KEY (id);


--
-- Name: user_sessions user_sessions_refresh_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT user_sessions_refresh_token_hash_key UNIQUE (refresh_token_hash);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: verification_tokens verification_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verification_tokens
    ADD CONSTRAINT verification_tokens_pkey PRIMARY KEY (id);


--
-- Name: verification_tokens verification_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verification_tokens
    ADD CONSTRAINT verification_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: webhook_subscriptions webhook_subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.webhook_subscriptions
    ADD CONSTRAINT webhook_subscriptions_pkey PRIMARY KEY (id);


--
-- Name: idx_addresses_city_district; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_addresses_city_district ON public.addresses USING btree (city, district);


--
-- Name: idx_addresses_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_addresses_user_id ON public.addresses USING btree (user_id);


--
-- Name: idx_audit_logs_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_actor ON public.audit_logs USING btree (actor_id);


--
-- Name: idx_audit_logs_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_entity ON public.audit_logs USING btree (entity_type, entity_id);


--
-- Name: idx_automation_tasks_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_automation_tasks_due ON public.automation_tasks USING btree (status, run_after);


--
-- Name: idx_campaign_redemption_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_campaign_redemption_customer ON public.campaign_redemptions USING btree (campaign_id, customer_id);


--
-- Name: idx_campaigns_active_dates; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_campaigns_active_dates ON public.campaigns USING btree (active, start_date, end_date);


--
-- Name: idx_card_management_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_card_management_customer ON public.payment_card_management_sessions USING btree (customer_id, created_at DESC);


--
-- Name: idx_card_management_status_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_card_management_status_expiry ON public.payment_card_management_sessions USING btree (status, expires_at);


--
-- Name: idx_checkout_sessions_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_checkout_sessions_customer ON public.payment_checkout_sessions USING btree (customer_id, created_at DESC);


--
-- Name: idx_checkout_sessions_status_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_checkout_sessions_status_expiry ON public.payment_checkout_sessions USING btree (status, expires_at);


--
-- Name: idx_checkout_sessions_subscription; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_checkout_sessions_subscription ON public.payment_checkout_sessions USING btree (subscription_id, created_at DESC);


--
-- Name: idx_commission_rules_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_commission_rules_active ON public.commission_rules USING btree (store_id, active, effective_from);


--
-- Name: idx_complaint_attachments_complaint; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_complaint_attachments_complaint ON public.complaint_attachments USING btree (complaint_id);


--
-- Name: idx_couriers_store_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_couriers_store_email ON public.couriers USING btree (store_id, lower((email)::text));


--
-- Name: idx_deliveries_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_deliveries_date ON public.subscription_deliveries USING btree (delivery_date);


--
-- Name: idx_deliveries_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_deliveries_status ON public.subscription_deliveries USING btree (status);


--
-- Name: idx_deliveries_subscription_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_deliveries_subscription_id ON public.subscription_deliveries USING btree (subscription_id);


--
-- Name: idx_delivery_compensation_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_compensation_status ON public.subscription_deliveries USING btree (compensation_status) WHERE (compensation_status IS NOT NULL);


--
-- Name: idx_delivery_modification_history_store_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_modification_history_store_pending ON public.delivery_modification_history USING btree (request_status, created_at DESC);


--
-- Name: idx_delivery_modification_history_subscription; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_modification_history_subscription ON public.delivery_modification_history USING btree (subscription_id, created_at DESC);


--
-- Name: idx_extension_requests_store_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_extension_requests_store_lookup ON public.subscription_extension_requests USING btree (status, created_at);


--
-- Name: idx_finance_reconciliations_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_reconciliations_status ON public.finance_reconciliations USING btree (status, reconciliation_date DESC);


--
-- Name: idx_meal_balance_transactions_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meal_balance_transactions_customer ON public.meal_balance_transactions USING btree (account_id, created_at DESC);


--
-- Name: idx_menu_allergen; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_menu_allergen ON public.menu_allergens USING btree (allergen);


--
-- Name: idx_menu_diet_tag; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_menu_diet_tag ON public.menu_diet_tags USING btree (diet_tag);


--
-- Name: idx_menu_gallery_images_menu_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_menu_gallery_images_menu_order ON public.menu_gallery_images USING btree (menu_id, sort_order) WHERE (deleted_at IS NULL);


--
-- Name: idx_menus_store_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_menus_store_id ON public.menus USING btree (store_id);


--
-- Name: idx_notification_events_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_events_due ON public.notification_events USING btree (status, next_attempt_at, created_at);


--
-- Name: idx_notification_events_retry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_events_retry ON public.notification_events USING btree (status, next_attempt_at);


--
-- Name: idx_notifications_read; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_read ON public.notifications USING btree (user_id, read);


--
-- Name: idx_notifications_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_user_id ON public.notifications USING btree (user_id);


--
-- Name: idx_payment_allocations_delivery; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_allocations_delivery ON public.payment_allocations USING btree (delivery_id);


--
-- Name: idx_payment_methods_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_methods_customer ON public.payment_methods USING btree (customer_id, active);


--
-- Name: idx_payments_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_customer ON public.payments USING btree (customer_id, created_at DESC);


--
-- Name: idx_payments_dunning_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_dunning_due ON public.payments USING btree (status, next_retry_at) WHERE (next_retry_at IS NOT NULL);


--
-- Name: idx_payments_store; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_store ON public.payments USING btree (store_id, created_at DESC);


--
-- Name: idx_payments_subscription; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_subscription ON public.payments USING btree (subscription_id);


--
-- Name: idx_payout_adjustments_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_adjustments_pending ON public.seller_payout_adjustments USING btree (store_id, id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_product_analytics_events_name_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_analytics_events_name_time ON public.product_analytics_events USING btree (event_name, occurred_at);


--
-- Name: idx_provider_operations_recovery; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_provider_operations_recovery ON public.provider_operations USING btree (status, provider_completed_at) WHERE (local_applied_at IS NULL);


--
-- Name: idx_refunds_payment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refunds_payment ON public.refunds USING btree (payment_id);


--
-- Name: idx_refunds_retry_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refunds_retry_due ON public.refunds USING btree (status, next_retry_at) WHERE ((status)::text = 'FAILED'::text);


--
-- Name: idx_reviews_store_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_store_id ON public.reviews USING btree (store_id);


--
-- Name: idx_risk_cases_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_cases_status ON public.risk_cases USING btree (status, severity, created_at DESC);


--
-- Name: idx_seller_documents_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_seller_documents_status ON public.seller_documents USING btree (store_id, verification_status);


--
-- Name: idx_seller_sla_events_store_occurred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_seller_sla_events_store_occurred ON public.seller_sla_events USING btree (store_id, occurred_at DESC);


--
-- Name: idx_service_areas_city_district; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_service_areas_city_district ON public.service_areas USING btree (city, district);


--
-- Name: idx_service_demands_region_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_service_demands_region_status ON public.service_demands USING btree (city, district, neighborhood, status);


--
-- Name: idx_store_capacity_overrides_store_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_store_capacity_overrides_store_date ON public.store_capacity_overrides USING btree (store_id, capacity_date);


--
-- Name: idx_store_category_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_store_category_category ON public.store_category_labels USING btree (category);


--
-- Name: idx_store_delivery_slots_store_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_store_delivery_slots_store_id ON public.store_delivery_slots USING btree (store_id);


--
-- Name: idx_store_distance_rules_store_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_store_distance_rules_store_id ON public.store_distance_rules USING btree (store_id);


--
-- Name: idx_store_staff_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_store_staff_user ON public.store_staff USING btree (user_id, status);


--
-- Name: idx_store_views_user_recent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_store_views_user_recent ON public.store_views USING btree (user_id, viewed_at DESC);


--
-- Name: idx_stores_coordinates; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stores_coordinates ON public.stores USING btree (latitude, longitude);


--
-- Name: idx_stores_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stores_status ON public.stores USING btree (status);


--
-- Name: idx_subscription_deliveries_capacity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscription_deliveries_capacity ON public.subscription_deliveries USING btree (delivery_date, status, subscription_id);


--
-- Name: idx_subscription_freezes_subscription; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscription_freezes_subscription ON public.subscription_freezes USING btree (subscription_id, start_date);


--
-- Name: idx_subscriptions_auto_renew_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscriptions_auto_renew_due ON public.subscriptions USING btree (end_date) WHERE (auto_renew = true);


--
-- Name: idx_subscriptions_customer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscriptions_customer_id ON public.subscriptions USING btree (customer_id);


--
-- Name: idx_subscriptions_end_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscriptions_end_date ON public.subscriptions USING btree (end_date);


--
-- Name: idx_subscriptions_menu_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscriptions_menu_version ON public.subscriptions USING btree (menu_version_id);


--
-- Name: idx_subscriptions_start_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscriptions_start_date ON public.subscriptions USING btree (start_date);


--
-- Name: idx_subscriptions_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscriptions_status ON public.subscriptions USING btree (status);


--
-- Name: idx_subscriptions_store_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscriptions_store_id ON public.subscriptions USING btree (store_id);


--
-- Name: idx_subscriptions_store_pending_deadline; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscriptions_store_pending_deadline ON public.subscriptions USING btree (store_id, status, approval_deadline_at);


--
-- Name: idx_user_sessions_user_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_sessions_user_active ON public.user_sessions USING btree (user_id, revoked_at, expires_at);


--
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- Name: idx_users_referral_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_users_referral_code ON public.users USING btree (referral_code) WHERE (referral_code IS NOT NULL);


--
-- Name: idx_users_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_role ON public.users USING btree (role);


--
-- Name: idx_verification_tokens_user_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_verification_tokens_user_type ON public.verification_tokens USING btree (user_id, token_type, expires_at DESC);


--
-- Name: uk_complaints_compensation_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_complaints_compensation_code ON public.complaints USING btree (compensation_code) WHERE (compensation_code IS NOT NULL);


--
-- Name: uk_subscriptions_customer_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_subscriptions_customer_idempotency ON public.subscriptions USING btree (customer_id, idempotency_key) WHERE (idempotency_key IS NOT NULL);


--
-- Name: uq_seller_payout_items_payment; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_seller_payout_items_payment ON public.seller_payout_items USING btree (payment_id) WHERE (refund_id IS NULL);


--
-- Name: uq_subscription_adjustment_delivery; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_subscription_adjustment_delivery ON public.subscription_adjustments USING btree (delivery_id) WHERE (delivery_id IS NOT NULL);


--
-- Name: uq_subscription_extension_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_subscription_extension_pending ON public.subscription_extension_requests USING btree (subscription_id) WHERE (((status)::text = 'PENDING'::text) AND (deleted_at IS NULL));


--
-- Name: addresses addresses_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.addresses
    ADD CONSTRAINT addresses_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: business_hours business_hours_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_hours
    ADD CONSTRAINT business_hours_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: campaign_redemptions campaign_redemptions_campaign_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_redemptions
    ADD CONSTRAINT campaign_redemptions_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES public.campaigns(id);


--
-- Name: campaign_redemptions campaign_redemptions_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_redemptions
    ADD CONSTRAINT campaign_redemptions_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: campaign_redemptions campaign_redemptions_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_redemptions
    ADD CONSTRAINT campaign_redemptions_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: campaigns campaigns_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaigns
    ADD CONSTRAINT campaigns_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES public.menus(id);


--
-- Name: campaigns campaigns_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaigns
    ADD CONSTRAINT campaigns_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: campaigns campaigns_target_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaigns
    ADD CONSTRAINT campaigns_target_customer_id_fkey FOREIGN KEY (target_customer_id) REFERENCES public.users(id);


--
-- Name: commission_rules commission_rules_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commission_rules
    ADD CONSTRAINT commission_rules_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: complaint_attachments complaint_attachments_complaint_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.complaint_attachments
    ADD CONSTRAINT complaint_attachments_complaint_id_fkey FOREIGN KEY (complaint_id) REFERENCES public.complaints(id);


--
-- Name: complaints complaints_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.complaints
    ADD CONSTRAINT complaints_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: complaints complaints_delivery_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.complaints
    ADD CONSTRAINT complaints_delivery_id_fkey FOREIGN KEY (delivery_id) REFERENCES public.subscription_deliveries(id);


--
-- Name: complaints complaints_resolved_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.complaints
    ADD CONSTRAINT complaints_resolved_by_user_id_fkey FOREIGN KEY (resolved_by_user_id) REFERENCES public.users(id);


--
-- Name: complaints complaints_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.complaints
    ADD CONSTRAINT complaints_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: complaints complaints_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.complaints
    ADD CONSTRAINT complaints_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: consent_records consent_records_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consent_records
    ADD CONSTRAINT consent_records_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: couriers couriers_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.couriers
    ADD CONSTRAINT couriers_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: customer_profiles customer_profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_profiles
    ADD CONSTRAINT customer_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: delivery_modification_history delivery_modification_history_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: delivery_modification_history delivery_modification_history_decided_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_decided_by_user_id_fkey FOREIGN KEY (decided_by_user_id) REFERENCES public.users(id);


--
-- Name: delivery_modification_history delivery_modification_history_delivery_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_delivery_id_fkey FOREIGN KEY (delivery_id) REFERENCES public.subscription_deliveries(id);


--
-- Name: delivery_modification_history delivery_modification_history_new_address_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_new_address_id_fkey FOREIGN KEY (new_address_id) REFERENCES public.addresses(id);


--
-- Name: delivery_modification_history delivery_modification_history_new_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_new_menu_id_fkey FOREIGN KEY (new_menu_id) REFERENCES public.menus(id);


--
-- Name: delivery_modification_history delivery_modification_history_old_address_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_old_address_id_fkey FOREIGN KEY (old_address_id) REFERENCES public.addresses(id);


--
-- Name: delivery_modification_history delivery_modification_history_old_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_old_menu_id_fkey FOREIGN KEY (old_menu_id) REFERENCES public.menus(id);


--
-- Name: delivery_modification_history delivery_modification_history_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payments(id);


--
-- Name: delivery_modification_history delivery_modification_history_refund_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_refund_id_fkey FOREIGN KEY (refund_id) REFERENCES public.refunds(id);


--
-- Name: delivery_modification_history delivery_modification_history_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_modification_history
    ADD CONSTRAINT delivery_modification_history_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: delivery_proofs delivery_proofs_delivery_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_proofs
    ADD CONSTRAINT delivery_proofs_delivery_id_fkey FOREIGN KEY (delivery_id) REFERENCES public.subscription_deliveries(id);


--
-- Name: favorites favorites_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.favorites
    ADD CONSTRAINT favorites_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: favorites favorites_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.favorites
    ADD CONSTRAINT favorites_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: finance_reconciliations finance_reconciliations_assigned_admin_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_reconciliations
    ADD CONSTRAINT finance_reconciliations_assigned_admin_id_fkey FOREIGN KEY (assigned_admin_id) REFERENCES public.users(id);


--
-- Name: subscription_deliveries fk_delivery_makeup_source; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_deliveries
    ADD CONSTRAINT fk_delivery_makeup_source FOREIGN KEY (makeup_source_delivery_id) REFERENCES public.subscription_deliveries(id);


--
-- Name: invoices invoices_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payments(id);


--
-- Name: invoices invoices_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: meal_balance_accounts meal_balance_accounts_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_balance_accounts
    ADD CONSTRAINT meal_balance_accounts_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: meal_balance_transactions meal_balance_transactions_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_balance_transactions
    ADD CONSTRAINT meal_balance_transactions_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.meal_balance_accounts(id);


--
-- Name: meal_balance_transactions meal_balance_transactions_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_balance_transactions
    ADD CONSTRAINT meal_balance_transactions_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payments(id);


--
-- Name: meal_balance_transactions meal_balance_transactions_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meal_balance_transactions
    ADD CONSTRAINT meal_balance_transactions_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: menu_allergens menu_allergens_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_allergens
    ADD CONSTRAINT menu_allergens_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES public.menus(id) ON DELETE CASCADE;


--
-- Name: menu_diet_tags menu_diet_tags_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_diet_tags
    ADD CONSTRAINT menu_diet_tags_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES public.menus(id) ON DELETE CASCADE;


--
-- Name: menu_gallery_images menu_gallery_images_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_gallery_images
    ADD CONSTRAINT menu_gallery_images_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES public.menus(id) ON DELETE CASCADE;


--
-- Name: menu_items menu_items_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_items
    ADD CONSTRAINT menu_items_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES public.menus(id);


--
-- Name: menu_schedule_versions menu_schedule_versions_menu_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_schedule_versions
    ADD CONSTRAINT menu_schedule_versions_menu_version_id_fkey FOREIGN KEY (menu_version_id) REFERENCES public.menu_versions(id) ON DELETE CASCADE;


--
-- Name: menu_schedules menu_schedules_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_schedules
    ADD CONSTRAINT menu_schedules_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES public.menus(id);


--
-- Name: menu_versions menu_versions_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_versions
    ADD CONSTRAINT menu_versions_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES public.menus(id);


--
-- Name: menus menus_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menus
    ADD CONSTRAINT menus_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: notification_events notification_events_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_events
    ADD CONSTRAINT notification_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: notification_preferences notification_preferences_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT notification_preferences_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: notifications notifications_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: payment_allocations payment_allocations_delivery_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_allocations
    ADD CONSTRAINT payment_allocations_delivery_id_fkey FOREIGN KEY (delivery_id) REFERENCES public.subscription_deliveries(id);


--
-- Name: payment_allocations payment_allocations_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_allocations
    ADD CONSTRAINT payment_allocations_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payments(id);


--
-- Name: payment_attempts payment_attempts_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_attempts
    ADD CONSTRAINT payment_attempts_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payments(id);


--
-- Name: payment_card_management_sessions payment_card_management_sessions_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_card_management_sessions
    ADD CONSTRAINT payment_card_management_sessions_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: payment_checkout_sessions payment_checkout_sessions_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_checkout_sessions
    ADD CONSTRAINT payment_checkout_sessions_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: payment_checkout_sessions payment_checkout_sessions_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_checkout_sessions
    ADD CONSTRAINT payment_checkout_sessions_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: payment_methods payment_methods_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_methods
    ADD CONSTRAINT payment_methods_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: payments payments_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: payments payments_payment_method_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_payment_method_id_fkey FOREIGN KEY (payment_method_id) REFERENCES public.payment_methods(id);


--
-- Name: payments payments_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: payments payments_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: push_subscriptions push_subscriptions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.push_subscriptions
    ADD CONSTRAINT push_subscriptions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: refunds refunds_payment_allocation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT refunds_payment_allocation_id_fkey FOREIGN KEY (payment_allocation_id) REFERENCES public.payment_allocations(id);


--
-- Name: refunds refunds_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT refunds_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payments(id);


--
-- Name: refunds refunds_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT refunds_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: reviews reviews_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: reviews reviews_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: reviews reviews_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: risk_cases risk_cases_assigned_admin_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_cases
    ADD CONSTRAINT risk_cases_assigned_admin_id_fkey FOREIGN KEY (assigned_admin_id) REFERENCES public.users(id);


--
-- Name: seller_documents seller_documents_reviewed_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_documents
    ADD CONSTRAINT seller_documents_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES public.users(id);


--
-- Name: seller_documents seller_documents_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_documents
    ADD CONSTRAINT seller_documents_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: seller_payout_adjustments seller_payout_adjustments_last_applied_payout_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_adjustments
    ADD CONSTRAINT seller_payout_adjustments_last_applied_payout_id_fkey FOREIGN KEY (last_applied_payout_id) REFERENCES public.seller_payouts(id);


--
-- Name: seller_payout_adjustments seller_payout_adjustments_refund_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_adjustments
    ADD CONSTRAINT seller_payout_adjustments_refund_id_fkey FOREIGN KEY (refund_id) REFERENCES public.refunds(id);


--
-- Name: seller_payout_adjustments seller_payout_adjustments_source_payout_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_adjustments
    ADD CONSTRAINT seller_payout_adjustments_source_payout_id_fkey FOREIGN KEY (source_payout_id) REFERENCES public.seller_payouts(id);


--
-- Name: seller_payout_adjustments seller_payout_adjustments_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_adjustments
    ADD CONSTRAINT seller_payout_adjustments_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: seller_payout_items seller_payout_items_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_items
    ADD CONSTRAINT seller_payout_items_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payments(id);


--
-- Name: seller_payout_items seller_payout_items_payout_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_items
    ADD CONSTRAINT seller_payout_items_payout_id_fkey FOREIGN KEY (payout_id) REFERENCES public.seller_payouts(id);


--
-- Name: seller_payout_items seller_payout_items_refund_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payout_items
    ADD CONSTRAINT seller_payout_items_refund_id_fkey FOREIGN KEY (refund_id) REFERENCES public.refunds(id);


--
-- Name: seller_payouts seller_payouts_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_payouts
    ADD CONSTRAINT seller_payouts_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: seller_profiles seller_profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_profiles
    ADD CONSTRAINT seller_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: seller_sla_events seller_sla_events_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_sla_events
    ADD CONSTRAINT seller_sla_events_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: seller_sla_events seller_sla_events_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seller_sla_events
    ADD CONSTRAINT seller_sla_events_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: service_areas service_areas_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_areas
    ADD CONSTRAINT service_areas_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: service_demands service_demands_address_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_demands
    ADD CONSTRAINT service_demands_address_id_fkey FOREIGN KEY (address_id) REFERENCES public.addresses(id);


--
-- Name: service_demands service_demands_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_demands
    ADD CONSTRAINT service_demands_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: store_capacity_overrides store_capacity_overrides_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_capacity_overrides
    ADD CONSTRAINT store_capacity_overrides_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: store_category_labels store_category_labels_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_category_labels
    ADD CONSTRAINT store_category_labels_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id) ON DELETE CASCADE;


--
-- Name: store_closed_dates store_closed_dates_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_closed_dates
    ADD CONSTRAINT store_closed_dates_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: store_delivery_slots store_delivery_slots_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_delivery_slots
    ADD CONSTRAINT store_delivery_slots_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: store_distance_rules store_distance_rules_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_distance_rules
    ADD CONSTRAINT store_distance_rules_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: store_onboarding store_onboarding_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_onboarding
    ADD CONSTRAINT store_onboarding_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: store_staff store_staff_invited_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_staff
    ADD CONSTRAINT store_staff_invited_by_fkey FOREIGN KEY (invited_by) REFERENCES public.users(id);


--
-- Name: store_staff store_staff_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_staff
    ADD CONSTRAINT store_staff_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: store_staff store_staff_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_staff
    ADD CONSTRAINT store_staff_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: store_views store_views_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_views
    ADD CONSTRAINT store_views_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id) ON DELETE CASCADE;


--
-- Name: store_views store_views_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_views
    ADD CONSTRAINT store_views_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: stores stores_seller_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stores
    ADD CONSTRAINT stores_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES public.seller_profiles(id);


--
-- Name: subscription_adjustments subscription_adjustments_delivery_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_adjustments
    ADD CONSTRAINT subscription_adjustments_delivery_id_fkey FOREIGN KEY (delivery_id) REFERENCES public.subscription_deliveries(id);


--
-- Name: subscription_adjustments subscription_adjustments_refund_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_adjustments
    ADD CONSTRAINT subscription_adjustments_refund_id_fkey FOREIGN KEY (refund_id) REFERENCES public.refunds(id);


--
-- Name: subscription_adjustments subscription_adjustments_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_adjustments
    ADD CONSTRAINT subscription_adjustments_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: subscription_deliveries subscription_deliveries_address_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_deliveries
    ADD CONSTRAINT subscription_deliveries_address_id_fkey FOREIGN KEY (address_id) REFERENCES public.addresses(id);


--
-- Name: subscription_deliveries subscription_deliveries_changed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_deliveries
    ADD CONSTRAINT subscription_deliveries_changed_by_user_id_fkey FOREIGN KEY (changed_by_user_id) REFERENCES public.users(id);


--
-- Name: subscription_deliveries subscription_deliveries_courier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_deliveries
    ADD CONSTRAINT subscription_deliveries_courier_id_fkey FOREIGN KEY (courier_id) REFERENCES public.couriers(id);


--
-- Name: subscription_deliveries subscription_deliveries_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_deliveries
    ADD CONSTRAINT subscription_deliveries_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES public.menus(id);


--
-- Name: subscription_deliveries subscription_deliveries_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_deliveries
    ADD CONSTRAINT subscription_deliveries_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: subscription_extension_requests subscription_extension_requests_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_extension_requests
    ADD CONSTRAINT subscription_extension_requests_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: subscription_extension_requests subscription_extension_requests_decided_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_extension_requests
    ADD CONSTRAINT subscription_extension_requests_decided_by_user_id_fkey FOREIGN KEY (decided_by_user_id) REFERENCES public.users(id);


--
-- Name: subscription_extension_requests subscription_extension_requests_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_extension_requests
    ADD CONSTRAINT subscription_extension_requests_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: subscription_freezes subscription_freezes_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_freezes
    ADD CONSTRAINT subscription_freezes_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: subscription_freezes subscription_freezes_subscription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_freezes
    ADD CONSTRAINT subscription_freezes_subscription_id_fkey FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id);


--
-- Name: subscriptions subscriptions_address_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_address_id_fkey FOREIGN KEY (address_id) REFERENCES public.addresses(id);


--
-- Name: subscriptions subscriptions_campaign_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES public.campaigns(id);


--
-- Name: subscriptions subscriptions_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.users(id);


--
-- Name: subscriptions subscriptions_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES public.menus(id);


--
-- Name: subscriptions subscriptions_menu_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_menu_version_id_fkey FOREIGN KEY (menu_version_id) REFERENCES public.menu_versions(id);


--
-- Name: subscriptions subscriptions_payment_method_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_payment_method_id_fkey FOREIGN KEY (payment_method_id) REFERENCES public.payment_methods(id);


--
-- Name: subscriptions subscriptions_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id);


--
-- Name: user_data_requests user_data_requests_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_data_requests
    ADD CONSTRAINT user_data_requests_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_sessions user_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT user_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: verification_tokens verification_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.verification_tokens
    ADD CONSTRAINT verification_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


-- Static reference data and mandatory runtime defaults. These were inserted by
-- the former migration history and are required on a completely new database.
INSERT INTO public.bank_catalog (name, legal_name, bank_type) VALUES
    ('Akbank', 'Akbank T.A.Ş.', 'MEVDUAT'),
    ('Albaraka Türk Katılım', 'Albaraka Türk Katılım Bankası A.Ş.', 'KATILIM'),
    ('Alternatif Bank', 'Alternatifbank A.Ş.', 'MEVDUAT'),
    ('Anadolubank', 'Anadolubank A.Ş.', 'MEVDUAT'),
    ('Burgan Bank', 'Burgan Bank A.Ş.', 'MEVDUAT'),
    ('DenizBank', 'Denizbank A.Ş.', 'MEVDUAT'),
    ('Emlak Katılım', 'Türkiye Emlak Katılım Bankası A.Ş.', 'KATILIM'),
    ('Enpara Bank', 'Enpara Bank A.Ş.', 'MEVDUAT'),
    ('Fibabanka', 'Fibabanka A.Ş.', 'MEVDUAT'),
    ('Garanti BBVA', 'Türkiye Garanti Bankası A.Ş.', 'MEVDUAT'),
    ('Halkbank', 'Türkiye Halk Bankası A.Ş.', 'MEVDUAT'),
    ('HSBC', 'HSBC Bank A.Ş.', 'MEVDUAT'),
    ('ING', 'ING Bank A.Ş.', 'MEVDUAT'),
    ('İş Bankası', 'Türkiye İş Bankası A.Ş.', 'MEVDUAT'),
    ('Kuveyt Türk', 'Kuveyt Türk Katılım Bankası A.Ş.', 'KATILIM'),
    ('Odeabank', 'Odea Bank A.Ş.', 'MEVDUAT'),
    ('QNB', 'QNB Bank A.Ş.', 'MEVDUAT'),
    ('Şekerbank', 'Şekerbank T.A.Ş.', 'MEVDUAT'),
    ('TEB', 'Türk Ekonomi Bankası A.Ş.', 'MEVDUAT'),
    ('Türkiye Finans Katılım', 'Türkiye Finans Katılım Bankası A.Ş.', 'KATILIM'),
    ('Vakıf Katılım', 'Vakıf Katılım Bankası A.Ş.', 'KATILIM'),
    ('VakıfBank', 'Türkiye Vakıflar Bankası T.A.O.', 'MEVDUAT'),
    ('Yapı Kredi', 'Yapı ve Kredi Bankası A.Ş.', 'MEVDUAT'),
    ('Ziraat Bankası', 'Türkiye Cumhuriyeti Ziraat Bankası A.Ş.', 'MEVDUAT'),
    ('Ziraat Katılım', 'Ziraat Katılım Bankası A.Ş.', 'KATILIM');

INSERT INTO public.platform_settings (setting_key, setting_value, description) VALUES
    ('SUBSCRIPTION_APPROVAL_SLA_HOURS', '72', 'Abonelik talebi onay süresi (saat)'),
    ('MIN_SUBSCRIPTION_SERVICE_DAYS', '5', 'Minimum abonelik hizmet günü'),
    ('COMMISSION_RATE', '12.00', 'Global platform komisyon oranı (%)'),
    ('SUBSCRIPTION_REQUEST_MIN_LEAD_DAYS', '2', 'Abonelik başlangıcı için minimum hazırlık süresi (gün)'),
    ('FAILED_DELIVERY_COMPENSATION_SEARCH_DAYS', '90', 'Başarısız teslimat için telafi günü arama süresi'),
    ('SUBSCRIPTION_MAX_EXTENSION_DAYS', '730', 'Aboneliğin tek işlemde uzatılabileceği azami gün'),
    ('SUBSCRIPTION_DEFAULT_RENEWAL_PERIOD_DAYS', '28', 'Varsayılan otomatik yenileme dönemi (gün)'),
    ('SUBSCRIPTION_RENEWAL_PRICE_NOTICE_DAYS', '7', 'Yenileme fiyat değişikliği bildirim süresi (gün)'),
    ('DEFAULT_DELIVERY_CHANGE_CUTOFF_HOURS', '24', 'Mağazaya özel değer yoksa teslimat değişikliği son süresi (saat)'),
    ('STORE_CLOSED_DATE_NOTICE_DAYS', '2', 'Mağaza kapalı gün bildirimi için minimum süre (gün)'),
    ('COMPLAINT_COMPENSATION_COUPON_VALIDITY_DAYS', '90', 'Şikâyet telafi kuponu geçerlilik süresi (gün)'),
    ('SELLER_STAFF_INVITATION_EXPIRY_DAYS', '7', 'Satıcı personel daveti geçerlilik süresi (gün)'),
    ('SELLER_DOCUMENT_EXPIRY_WARNING_DAYS', '30', 'Satıcı belge bitiş uyarısı süresi (gün)'),
    ('COMPLAINT_RESPONSE_SLA_HOURS', '24', 'Şikâyet yanıt SLA süresi (saat)'),
    ('PAYMENT_MAX_ATTEMPTS', '3', 'Ödeme ve iade için azami deneme sayısı');


--
-- PostgreSQL database dump complete
--
