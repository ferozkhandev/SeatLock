CREATE USER catalog_user WITH PASSWORD 'catalog-db';
CREATE DATABASE catalog_db OWNER catalog_user;
REVOKE CONNECT ON DATABASE catalog_db FROM PUBLIC;

CREATE USER booking_user WITH PASSWORD 'booking-db';
CREATE DATABASE booking_db OWNER booking_user;
REVOKE CONNECT ON DATABASE booking_db FROM PUBLIC;

CREATE USER notification_user WITH PASSWORD 'notification-db';
CREATE DATABASE notification_db OWNER notification_user;
REVOKE CONNECT ON DATABASE notification_db FROM PUBLIC;