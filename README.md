# Query Optimiser
This project includes a Query Optimizer and an Estimator, that were built for SJDB (A MySQL like database designed by Dr Nicholas Gibbins)
* The Estimator: ```src/sjdb/Estimator.java``` accepts a database logical query and estimates the total cost in terms of disk accesses
* The Optimizer: ```src/sjdb/Optimiser.java``` optimises a given query by pushing down the Selects, creating Joins and adding Projects

