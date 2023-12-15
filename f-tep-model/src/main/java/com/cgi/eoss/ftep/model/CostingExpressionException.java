package com.cgi.eoss.ftep.model;

/**
 *
 * @author TTESEI
 */
public class CostingExpressionException extends RuntimeException {

    public CostingExpressionException(String string) {
        super(string);
    }

    public CostingExpressionException(String string, Throwable thrwbl) {
        super(string, thrwbl);
    }

}
