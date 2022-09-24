

DROP TABLE IF EXISTS act CASCADE;
create table act(
    actID SERIAL PRIMARY KEY,
    actname VARCHAR(100) NOT NULL,
    genre VARCHAR(20),
    members INTEGER NOT NULL CHECK (members >= 0), /*Check here, integer non-negative */
    standardfee INTEGER NOT NULL
);

DROP TABLE IF EXISTS venue CASCADE;
create table venue(
    venueid SERIAL PRIMARY KEY,
    venuename VARCHAR(100) NOT NULL,
    hirecost INTEGER NOT NULL, /*Check here, integer non-negative */
    capacity INTEGER NOT NULL CHECK (capacity >= 0) /*Trigger here, capacity <= tickets sold for particular gig at venue*/ /*Check here, integer non-negative */
);

DROP TABLE IF EXISTS gig CASCADE;
create table gig(
    gigID SERIAL PRIMARY KEY,
    venueid INTEGER NOT NULL,
    gigtitle VARCHAR(100) NOT NULL,
    gigdate TIMESTAMP NOT NULL,
    gigstatus VARCHAR(10) DEFAULT 'GoingAhead' NOT NULL, /*Check if its cancelled or going ahead*/
    FOREIGN KEY (venueid) REFERENCES venue(venueid),
    CHECK (gigstatus = 'Cancelled' OR gigstatus = 'GoingAhead')
);

DROP TABLE IF EXISTS act_gig CASCADE;
create table act_gig(
    actID INTEGER NOT NULL,
    gigID INTEGER NOT NULL,
    actfee INTEGER NOT NULL CHECK (actfee >= 0), /*Check here, integer non-negative */
    ontime TIMESTAMP NOT NULL, /*Trigger here, ontime >= gig.gigdate */
    duration INTEGER NOT NULL CHECK (duration >= 0), /*Check here, ontime + duration < 24:00:00 and duration >= 0 */
    CHECK (duration <= 120), /*Check here, no act lasts for > 2 hours */
    FOREIGN KEY (actID) REFERENCES act(actID),
    FOREIGN KEY (gigID) REFERENCES gig(gigID)
);

DROP TABLE IF EXISTS gig_ticket CASCADE;
create table gig_ticket(
    gigID INTEGER NOT NULL,
    pricetype VARCHAR(2) NOT NULL,
    cost INTEGER NOT NULL CHECK (cost >= 0), /*Check here, integer non-negative */
    FOREIGN KEY (gigID) REFERENCES gig(gigID)
);

DROP TABLE IF EXISTS ticket CASCADE;
create table ticket(
    ticketid SERIAL PRIMARY KEY,
    gigID INTEGER NOT NULL,
    pricetype VARCHAR(2) NOT NULL,
    cost INTEGER NOT NULL CHECK (cost >= 0), /*Check here, integer non-negative */
    CustomerName VARCHAR(100) NOT NULL,
    CustomerEmail VARCHAR(100) NOT NULL,
    FOREIGN KEY (gigID) REFERENCES gig(gigID)
);

/*Complex constraints (unable to be modelled with a simple CHECK constraint)*/


/*Tested and working
Trigger for acts starting after gigs*/
CREATE OR REPLACE FUNCTION ontimeInsertCheck() RETURNS trigger AS $$
    BEGIN
        IF (SELECT NEW.ontime < (SELECT gigdate FROM gig WHERE NEW.gigID = gig.gigID)) THEN
            RAISE EXCEPTION 'Act must start after gig';
        END IF;
        RETURN NEW;
    END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ontimeInsertCheck BEFORE INSERT OR UPDATE ON act_gig
    FOR EACH ROW EXECUTE FUNCTION ontimeInsertCheck();


/*Tested and working
Trigger for checking no more tickets sold than capacity of venue*/
CREATE OR REPLACE FUNCTION capacityInsertCheck() RETURNS trigger AS $$
    BEGIN
        IF (
            (SELECT COUNT(*) FROM ticket
            WHERE ticket.gigID = NEW.gigID) 
            >= (SELECT capacity FROM ticket
            JOIN gig ON ticket.gigID = gig.gigID
            JOIN venue ON gig.venueid = venue.venueid
            WHERE NEW.gigID = gig.gigID LIMIT 1)
            )
            THEN 
                RAISE EXCEPTION 'Venue is at full capacity';
        END IF;
        RETURN NEW;
    END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER capacityInsertCheck BEFORE INSERT OR UPDATE ON ticket
    FOR EACH ROW EXECUTE FUNCTION capacityInsertCheck();


/*Tested and working
Trigger for no overlap between acts on the same night*/
CREATE OR REPLACE FUNCTION actGigInsertCheck() RETURNS trigger AS $$
    BEGIN
        IF(
            /*Finds latest finish time of an act in the gig that starts before the start time
            of the new act, and checks if this latest finish time is more than the start
            time of the new act (If true, overlap has occured)*/
            ((SELECT (act_gig.ontime + CONCAT(act_gig.duration::varchar, ' minutes')::interval) 
            FROM act_gig JOIN gig ON act_gig.gigID = gig.gigID
            WHERE gig.gigID = NEW.gigID AND (act_gig.ontime <= NEW.ontime) 
            ORDER BY (act_gig.ontime + CONCAT(act_gig.duration::varchar, ' minutes')::interval) DESC LIMIT 1)
            >
            (SELECT NEW.ontime))
            )
            THEN
                RAISE EXCEPTION 'Act start and finish times cannot overlap in the same gig';
        END IF;
        IF(
            /*Finds earliest start time of an act in the gig that is greater than or equal
            to the start time of the new act, and checks if this earliest start time is
            less than the finish time of the new act (If true, overlap has occurred) */
            ((SELECT act_gig.ontime FROM act_gig JOIN
            gig ON act_gig.gigID = gig.gigID
            WHERE gig.gigID = NEW.gigID AND (act_gig.ontime >= NEW.ontime) 
            ORDER BY act_gig.ontime ASC LIMIT 1)
            <
            (SELECT NEW.ontime + CONCAT(NEW.duration::varchar, ' minutes')::interval))
            )
            THEN
                RAISE EXCEPTION 'Act start and finish times cannot overlap in the same gig';
        END IF;
        RETURN NEW;
    END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER actGigInsertCheck BEFORE INSERT OR UPDATE ON act_gig
    FOR EACH ROW EXECUTE FUNCTION actGigInsertCheck();


/*Tested and working
Trigger for no more than 20 minute interval in gig line-up*/
CREATE OR REPLACE FUNCTION actGigInsertCheck2() RETURNS trigger AS $$
    BEGIN
        IF(
            (SELECT (act_gig.ontime + CONCAT(act_gig.duration::varchar, ' minutes')::interval) 
            FROM act_gig JOIN gig ON act_gig.gigID = gig.gigID
            WHERE gig.gigID = NEW.gigID AND (act_gig.ontime <= NEW.ontime)
            ORDER BY act_gig.ontime DESC LIMIT 1)
            <
            (SELECT (NEW.ontime - '20 minutes'::interval))
            )
            THEN
                RAISE EXCEPTION 'No more than 20 minute interval between acts in a gig line-up';            
        END IF;
        IF(
            (SELECT (earliestST.ontime - '20 minutes'::interval) FROM (SELECT act_gig.ontime
            FROM act_gig JOIN gig ON act_gig.gigID = gig.gigID
            WHERE gig.gigID = NEW.gigID AND (act_gig.ontime >= NEW.ontime)
            ORDER BY act_gig.ontime ASC LIMIT 1) AS earliestST)
            >
            (SELECT (NEW.ontime + CONCAT(NEW.duration::varchar, ' minutes')::interval))
            )
            THEN
                RAISE EXCEPTION 'No more than 20 minute interval between acts in a gig line-up';
        END IF;
        RETURN NEW;
    END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER actGigInsertCheck2 BEFORE INSERT OR UPDATE ON act_gig
    FOR EACH ROW EXECUTE FUNCTION actGigInsertCheck2(); 


/*Tested and working
Trigger for deletion in act_gig, make sure intervals no greater than 20 minutes*/
CREATE OR REPLACE FUNCTION actGigDeleteCheck() RETURNS trigger AS $$
    BEGIN
        IF(
            (SELECT (act_gig.ontime + CONCAT(act_gig.duration::varchar, ' minutes')::interval) 
            FROM act_gig JOIN gig ON act_gig.gigID = gig.gigID
            WHERE gig.gigID = OLD.gigID AND (act_gig.ontime <= OLD.ontime)
            ORDER BY act_gig.ontime DESC LIMIT 1)
            <
            (SELECT (earliestST.ontime - '20 minutes'::interval) FROM (SELECT act_gig.ontime
            FROM act_gig JOIN gig ON act_gig.gigID = gig.gigID
            WHERE gig.gigID = OLD.gigID AND (act_gig.ontime >= OLD.ontime)
            ORDER BY act_gig.ontime ASC LIMIT 1) AS earliestST)
            )
            THEN
                RAISE EXCEPTION 'No more than 20 minute interval between acts in a gig line-up';
        END IF;
        RETURN OLD;
    END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER actGigDeleteCheck BEFORE DELETE ON act_gig
    FOR EACH ROW EXECUTE FUNCTION actGigDeleteCheck(); 


/*Tested and working
Trigger for first act of gig must start within 20 minutes of start time of gig*/
CREATE OR REPLACE FUNCTION actGigInsertCheck3() RETURNS trigger AS $$
    BEGIN
        IF (
            (SELECT (act_gig.ontime - ('20 minutes')::interval) AS ontime
            FROM act_gig JOIN gig ON act_gig.gigID = gig.gigID 
            WHERE gig.gigID = NEW.gigID ORDER BY ontime ASC LIMIT 1)
            >
            (SELECT gig.gigdate AS gigdate FROM gig WHERE gig.gigID = NEW.gigID)
            )
            THEN
                RAISE EXCEPTION 'First act of gig must start within 20 minutes of start time of gig';
        END IF;
        RETURN NEW;
    END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER actGigInsertCheck3 BEFORE INSERT OR UPDATE ON act_gig
    FOR EACH ROW EXECUTE FUNCTION actGigInsertCheck3();


/*Tested and working
Trigger for act not going past midnight*/
CREATE OR REPLACE FUNCTION actGigInsertCheck4() RETURNS trigger AS $$
    BEGIN
        IF (
            (SELECT (NEW.ontime + (CONCAT(NEW.duration::varchar, ' minutes'))::interval))
            >
            (SELECT (CONCAT((gig.gigdate::date), ' 24:00:00')::TIMESTAMP) FROM gig
            WHERE gig.gigID = NEW.gigID)
            )
            THEN
                RAISE EXCEPTION 'Act goes past midnight';
        END IF;
        RETURN NEW;
    END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER actGigInsertCheck4 BEFORE INSERT OR UPDATE ON act_gig
    FOR EACH ROW EXECUTE FUNCTION actGigInsertCheck4();
