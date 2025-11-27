CREATE TABLE ftep_terms (
    id                      BIGSERIAL PRIMARY KEY,
    version                 CHARACTER VARYING(255) NOT NULL UNIQUE,
    url                     CHARACTER VARYING(255) NOT NULL,
    valid_start             TIMESTAMP NOT NULL WITHOUT TIME ZONE,
    valid_end               TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE ftep_terms_acceptance (
    id                      BIGSERIAL PRIMARY KEY,
    owner                   BIGINT NOT NULL REFERENCES ftep_users (uid),
    accepted_time           TIMESTAMP WITHOUT TIME ZONE NOT NULL
);
CREATE INDEX ftep_terms_acceptance_owner_idx ON ftep_terms_acceptance (owner);
