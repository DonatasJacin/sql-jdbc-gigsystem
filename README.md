Solutions:

General:
Maintaining database state was done all in the schema.sql file through the use of triggers.
Any violation of database state as a result of insertions/deletions will be raised as an exception
by a trigger I have defined.

Option 1:
The select query simply joins the tables 'act' and 'act_gig', selecting actname, ontime and offtime (which is calculated through the use of CONCAT to generate intervals in form 'x minutes' which are then added to a time) with the condition that the actID from both tables matches.

Option 2:
This is a function with simple SELECT and INSERT operations, no error checking is done inside the
java function because if any errors do arise from the insert operations in regards to database state, the triggers in the schema.sql file will pick them up and return an SQL exception.

Option 3:
Same as option 2, simple SELECT and INSERT operations, with error checking handled by schema.sql file.

Option 4:
Here I first run a query which returns a boolean value, true if the given act is a headline act of the gig, and false if not. Then, the act is deleted from the gig and the boolean value is used to determine what is done next. If true then the cost of tickets to the gig are set to 0 and gigstatus is set to cancelled (This also happens if there is an SQL exception of interval being more than 20 minutes between acts in the gig). If false then nothing more is done. 

Option 5:
This option is handled with a single SQL query, first calculating the totalcost of a gig at a certain venue composed of certain acts, then dividing this value by the cost of each ticket - this is the amount of tickets required to break even. Finally, the number of tickets already sold for the gig are subtracted from the amount of tickets required to break even, returning the amount of tickets that still need to be sold to break even.
