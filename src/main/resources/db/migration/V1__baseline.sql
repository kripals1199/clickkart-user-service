-- V1__baseline.sql
-- Generated from the live schema Hibernate's ddl-auto produced, so this is exactly what already
-- exists rather than a hand-written approximation of it.
--
-- Existing databases are baselined at V1 and skip this file (spring.flyway.baseline-version=1).
-- A fresh database gets its whole schema from here - which is the point: the schema becomes a
-- reviewed artefact in git rather than a side effect of whatever the entity classes happened to
-- look like the last time the application started.


CREATE TABLE addresses (
    id bigint NOT NULL,
    created_by character varying(100) NOT NULL,
    created_date timestamp(6) with time zone NOT NULL,
    updated_by character varying(100) NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    city character varying(100) NOT NULL,
    contact_number character varying(15) NOT NULL,
    country character varying(60) NOT NULL,
    default_address boolean NOT NULL,
    deleted boolean NOT NULL,
    label character varying(20) NOT NULL,
    landmark character varying(150),
    line1 character varying(200) NOT NULL,
    line2 character varying(200),
    postal_code character varying(10) NOT NULL,
    recipient_name character varying(120) NOT NULL,
    state character varying(100) NOT NULL,
    profile_id bigint NOT NULL,
    CONSTRAINT addresses_label_check CHECK (((label)::text = ANY ((ARRAY['HOME'::character varying, 'WORK'::character varying, 'OTHER'::character varying])::text[])))
);

CREATE TABLE audit_chain_head (
    id bigint NOT NULL,
    entry_count bigint NOT NULL,
    last_entry_hash character varying(64) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE audit_log_entries (
    id bigint NOT NULL,
    action character varying(40) NOT NULL,
    actor character varying(64) NOT NULL,
    correlation_id character varying(64) NOT NULL,
    details character varying(1000),
    entry_hash character varying(64) NOT NULL,
    ip_address character varying(45) NOT NULL,
    occurred_at timestamp(6) with time zone NOT NULL,
    outcome character varying(10) NOT NULL,
    previous_entry_hash character varying(64) NOT NULL,
    user_agent character varying(512),
    CONSTRAINT audit_log_entries_action_check CHECK (((action)::text = ANY ((ARRAY['PROFILE_CREATED'::character varying, 'PROFILE_UPDATED'::character varying, 'PREFERENCES_UPDATED'::character varying, 'ADDRESS_ADDED'::character varying, 'ADDRESS_UPDATED'::character varying, 'ADDRESS_DELETED'::character varying, 'DEFAULT_ADDRESS_CHANGED'::character varying, 'SELLER_PROFILE_CREATED'::character varying, 'SELLER_PROFILE_UPDATED'::character varying, 'SELLER_VERIFICATION_RESET'::character varying, 'SELLER_VERIFICATION_DECIDED'::character varying, 'PROFILE_ERASED'::character varying])::text[]))),
    CONSTRAINT audit_log_entries_outcome_check CHECK (((outcome)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILURE'::character varying])::text[])))
);

CREATE SEQUENCE audit_log_entry_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE clickkart_user_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE seller_profiles (
    id bigint NOT NULL,
    created_by character varying(100) NOT NULL,
    created_date timestamp(6) with time zone NOT NULL,
    updated_by character varying(100) NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    business_name character varying(150) NOT NULL,
    gstin character varying(15) NOT NULL,
    pickup_address_id bigint,
    support_email character varying(254),
    support_phone character varying(15),
    verification_decided_at timestamp(6) with time zone,
    verification_note character varying(500),
    verification_status character varying(20) NOT NULL,
    profile_id bigint NOT NULL,
    CONSTRAINT seller_profiles_verification_status_check CHECK (((verification_status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying])::text[])))
);

CREATE TABLE user_profiles (
    id bigint NOT NULL,
    created_by character varying(100) NOT NULL,
    created_date timestamp(6) with time zone NOT NULL,
    updated_by character varying(100) NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    avatar_url character varying(500),
    date_of_birth date,
    display_name character varying(80),
    first_name character varying(60),
    gender character varying(20),
    last_name character varying(60),
    marketing_email_opt_in boolean NOT NULL,
    marketing_sms_opt_in boolean NOT NULL,
    preferred_currency character varying(3) NOT NULL,
    preferred_language character varying(10) NOT NULL,
    user_public_id character varying(64) NOT NULL,
    erased_at timestamp(6) with time zone,
    CONSTRAINT user_profiles_gender_check CHECK (((gender)::text = ANY ((ARRAY['MALE'::character varying, 'FEMALE'::character varying, 'OTHER'::character varying, 'PREFER_NOT_TO_SAY'::character varying])::text[])))
);

ALTER TABLE ONLY addresses
    ADD CONSTRAINT addresses_pkey PRIMARY KEY (id);

ALTER TABLE ONLY audit_chain_head
    ADD CONSTRAINT audit_chain_head_pkey PRIMARY KEY (id);

ALTER TABLE ONLY audit_log_entries
    ADD CONSTRAINT audit_log_entries_pkey PRIMARY KEY (id);

ALTER TABLE ONLY seller_profiles
    ADD CONSTRAINT seller_profiles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY seller_profiles
    ADD CONSTRAINT uk_seller_profiles_gstin UNIQUE (gstin);

ALTER TABLE ONLY seller_profiles
    ADD CONSTRAINT uk_seller_profiles_profile_id UNIQUE (profile_id);

ALTER TABLE ONLY user_profiles
    ADD CONSTRAINT uk_user_profiles_user_public_id UNIQUE (user_public_id);

ALTER TABLE ONLY user_profiles
    ADD CONSTRAINT user_profiles_pkey PRIMARY KEY (id);

CREATE INDEX idx_addresses_profile_id ON addresses USING btree (profile_id);

CREATE INDEX idx_addresses_profile_id_deleted ON addresses USING btree (profile_id, deleted);

CREATE INDEX idx_audit_log_entries_actor ON audit_log_entries USING btree (actor);

CREATE INDEX idx_audit_log_entries_correlation_id ON audit_log_entries USING btree (correlation_id);

CREATE INDEX idx_audit_log_entries_occurred_at ON audit_log_entries USING btree (occurred_at);

CREATE INDEX idx_seller_profiles_verification_status ON seller_profiles USING btree (verification_status);

CREATE INDEX idx_user_profiles_user_public_id ON user_profiles USING btree (user_public_id);

ALTER TABLE ONLY seller_profiles
    ADD CONSTRAINT fk6665087nlm2hl0tht3wibowne FOREIGN KEY (profile_id) REFERENCES user_profiles(id);

ALTER TABLE ONLY addresses
    ADD CONSTRAINT fkem30y0v2nmuoopc9v46i6g8mh FOREIGN KEY (profile_id) REFERENCES user_profiles(id);

