package crux.api.alpha;

import clojure.lang.Symbol;
import crux.api.ICruxAPI;
import crux.api.ICruxDatasource;
import clojure.lang.Keyword;

import java.util.*;
import java.util.stream.Collectors;

import static crux.api.alpha.ResultTuple.resultTuple;

public class Database {
    private final ICruxDatasource db;

    private Database(ICruxDatasource db) {
        this.db = db;
    }

    protected static Database database(ICruxAPI node) {
        return new Database(node.db());
    }

    protected static Database database(ICruxAPI node, Date validTime) {
        return new Database(node.db(validTime));
    }

    protected static Database database(ICruxAPI node, Date validTime, Date transactionTime) {
        return new Database(node.db(validTime, transactionTime));
    }

    /**
     * Submits a Query to the database, and returns a list of results from the query
     * @param query Query to perform on the Database
     * @return List of ResultTuple objects representing the results from the query
     * @see ResultTuple
     */
    public List<ResultTuple> query(Query query) {
        Collection<List<?>> queryResult = db.q(query.toEdn());
        List<Symbol> symbols = query.findSymbols();

        return queryResult.stream().map(tuple -> resultTuple(symbols, tuple)).collect(Collectors.toList());
    }

    public Collection<List<?>> query(String query) {
        return db.q(query);
    }

    /**
     * Retrieves a Document for an entity in the Database
     * @param id Id of entity to retrieve
     * @return Document representing the entity
     */
    public Document entity(CruxId id) {
        Map<Keyword, Object> entityDoc = db.entity(id.toEdn());
        if(entityDoc != null) {
            return Document.document(entityDoc);
        } else {
            return null;
        }

    }

    public EntityTx entityTx(CruxId id) {
        Map<Keyword, ?> entityTx = db.entityTx(id.toEdn());
        if(entityTx != null) {
            return EntityTx.entityTx(entityTx);
        } else {
            return null;
        }

    }
}
