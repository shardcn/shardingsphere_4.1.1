/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.shardingjdbc.jdbc.core.statement;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.shadow.rewrite.context.ShadowSQLRewriteContextDecorator;
import org.apache.shardingsphere.shadow.rewrite.judgement.ShadowJudgementEngine;
import org.apache.shardingsphere.shadow.rewrite.judgement.impl.SimpleJudgementEngine;
import org.apache.shardingsphere.shardingjdbc.jdbc.adapter.AbstractStatementAdapter;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.connection.ShadowConnection;
import org.apache.shardingsphere.sql.parser.binder.SQLStatementContextFactory;
import org.apache.shardingsphere.sql.parser.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationPropertyKey;
import org.apache.shardingsphere.underlying.rewrite.SQLRewriteEntry;
import org.apache.shardingsphere.underlying.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.underlying.rewrite.engine.SQLRewriteEngine;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

/**
 * Shadow statement.
 */
@Slf4j
public final class ShadowStatement extends AbstractStatementAdapter {
    
    @Getter
    private final ShadowConnection connection;
    
    private final ShadowStatementGenerator shadowStatementGenerator;
    
    private SQLStatementContext sqlStatementContext;
    
    private Statement statement;
    
    private ResultSet resultSet;

    private boolean isShadowSQL;
    
    public ShadowStatement(final ShadowConnection connection) {
        this(connection, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
    }
    
    public ShadowStatement(final ShadowConnection connection, final int resultSetType, final int resultSetConcurrency) {
        this(connection, resultSetType, resultSetConcurrency, ResultSet.HOLD_CURSORS_OVER_COMMIT);
    }
    
    public ShadowStatement(final ShadowConnection connection, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) {
        super(Statement.class);
        this.connection = connection;
        shadowStatementGenerator = new ShadowStatementGenerator(resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public ResultSet executeQuery(final String sql) throws SQLException {
        resultSet = getStatementAndReplay(sql).executeQuery(rewriteSQL(sql));
        return resultSet;
    }
    
    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return statement.getGeneratedKeys();
    }
    
    @Override
    public int executeUpdate(final String sql) throws SQLException {
        return getStatementAndReplay(sql).executeUpdate(rewriteSQL(sql));
    }
    
    @Override
    public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        return getStatementAndReplay(sql).executeUpdate(rewriteSQL(sql), autoGeneratedKeys);
    }
    
    @Override
    public int executeUpdate(final String sql, final int[] columnIndexes) throws SQLException {
        return getStatementAndReplay(sql).executeUpdate(rewriteSQL(sql), columnIndexes);
    }
    
    @Override
    public int executeUpdate(final String sql, final String[] columnNames) throws SQLException {
        return getStatementAndReplay(sql).executeUpdate(rewriteSQL(sql), columnNames);
    }
    
    @Override
    public boolean execute(final String sql) throws SQLException {
        boolean result = getStatementAndReplay(sql).execute(rewriteSQL(sql));
        resultSet = statement.getResultSet();
        return result;
    }
    
    @Override
    public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
        boolean result = getStatementAndReplay(sql).execute(rewriteSQL(sql), autoGeneratedKeys);
        resultSet = statement.getResultSet();
        return result;
    }
    
    @Override
    public boolean execute(final String sql, final int[] columnIndexes) throws SQLException {
        boolean result = getStatementAndReplay(sql).execute(rewriteSQL(sql), columnIndexes);
        resultSet = statement.getResultSet();
        return result;
    }
    
    @Override
    public boolean execute(final String sql, final String[] columnNames) throws SQLException {
        boolean result = getStatementAndReplay(sql).execute(rewriteSQL(sql), columnNames);
        resultSet = statement.getResultSet();
        return result;
    }
    
    @Override
    public ResultSet getResultSet() {
        return resultSet;
    }
    
    @Override
    public int getResultSetConcurrency() {
        return shadowStatementGenerator.resultSetConcurrency;
    }
    
    @Override
    public int getResultSetType() {
        return shadowStatementGenerator.resultSetType;
    }
    
    @Override
    public int getResultSetHoldability() {
        return shadowStatementGenerator.resultSetHoldability;
    }
    
    @Override
    protected boolean isAccumulate() {
        return false;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    protected Collection<Statement> getRoutedStatements() {
        Collection<Statement> result = new LinkedList();
        if (null == statement) {
            return result;
        }
        result.add(statement);
        return result;
    }
    
    private Statement getStatementAndReplay(final String sql) throws SQLException {
        SQLStatement sqlStatement = connection.getRuntimeContext().getSqlParserEngine().parse(sql, false);
        sqlStatementContext = SQLStatementContextFactory.newInstance(connection.getRuntimeContext().getMetaData().getSchema(), sql, Collections.emptyList(), sqlStatement);
        ShadowJudgementEngine shadowJudgementEngine = new SimpleJudgementEngine(connection.getRuntimeContext().getRule(), sqlStatementContext);
        isShadowSQL = shadowJudgementEngine.isShadowSQL();
        Statement result = shadowStatementGenerator.createStatement();
        statement = result;
        return result;
    }
    
    private String rewriteSQL(final String sql) {
        SQLRewriteEntry sqlRewriteEntry = new SQLRewriteEntry(connection.getRuntimeContext().getMetaData().getSchema(), connection.getRuntimeContext().getProperties());
        sqlRewriteEntry.registerDecorator(connection.getRuntimeContext().getRule(), new ShadowSQLRewriteContextDecorator());
        SQLRewriteContext sqlRewriteContext = sqlRewriteEntry.createSQLRewriteContext(sql, Collections.emptyList(), sqlStatementContext, null);
        String result = new SQLRewriteEngine().rewrite(sqlRewriteContext).getSql();
        showSQL(result);
        return result;
    }
    
    private void showSQL(final String sql) {
        if (connection.getRuntimeContext().getProperties().<Boolean>getValue(ConfigurationPropertyKey.SQL_SHOW)) {
            log.info("Rule Type: shadow");
            log.info("SQL: {} ::: IsShadowSQL: {}", sql, isShadowSQL);
        }
    }
    
    @RequiredArgsConstructor
    private final class ShadowStatementGenerator {
        
        private final int resultSetType;
        
        private final int resultSetConcurrency;
        
        private final int resultSetHoldability;
        
        private Statement createStatement() throws SQLException {
            if (-1 != resultSetType && -1 != resultSetConcurrency && -1 != resultSetHoldability) {
                return isShadowSQL ? connection.getShadowConnection().createStatement(resultSetType, resultSetConcurrency, resultSetHoldability)
                        : connection.getActualConnection().createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
            }
            if (-1 != resultSetType && -1 != resultSetConcurrency) {
                return isShadowSQL ? connection.getShadowConnection().createStatement(resultSetType, resultSetConcurrency)
                        : connection.getActualConnection().createStatement(resultSetType, resultSetConcurrency);
            }
            return isShadowSQL ? connection.getShadowConnection().createStatement() : connection.getActualConnection().createStatement();
        }
    }
}
