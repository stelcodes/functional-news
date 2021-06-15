CREATE EXTENSION pgcrypto;

DROP TABLE IF EXISTS upvotes;
DROP TABLE IF EXISTS comments;
DROP TABLE IF EXISTS submissions;
DROP TABLE IF EXISTS users;
DROP FUNCTION IF EXISTS minutes_age;
DROP FUNCTION IF EXISTS hotness;

CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  email TEXT NOT NULL UNIQUE,
  password TEXT NOT NULL,
  bio TEXT,
  created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE submissions (
  id SERIAL PRIMARY KEY,
  url TEXT NOT NULL,
  created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  userid INTEGER REFERENCES users (id) ON DELETE CASCADE NOT NULL,
  title TEXT NOT NULL,
  UNIQUE (url, userid)
);

CREATE TABLE comments (
  id SERIAL PRIMARY KEY,
  userid INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  submissionid INTEGER NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  body TEXT NOT NULL,
  created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE upvotes (
  id SERIAL PRIMARY KEY,
  userid INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  submissionid INTEGER NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  UNIQUE (userid, submissionid)
);

CREATE FUNCTION minutes_age(created timestamp) RETURNS integer AS $$
  DECLARE
  age real := abs(extract(epoch from now() - created)/60);
  BEGIN
    RETURN CAST (age AS integer);
  END; $$ LANGUAGE plpgsql;

CREATE FUNCTION hotness(upvotecount bigint, age integer) RETURNS real AS $$
  DECLARE hours real := (age / 60) + 0.001; gravity real := 1.8;
  BEGIN
    RETURN (upvotecount - 1) / power(hours, gravity);
  END; $$ LANGUAGE plpgsql;
