package io.datacatalog.dataset;

/**
 * One similarity-query hit: the entity together with its cosine distance to the query
 * vector. The distance rides along from the query itself — recomputing it afterwards would
 * mean a second pass over vectors the database already compared.
 */
public record NearestDataset(Dataset dataset, double distance) {}
