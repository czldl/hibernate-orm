/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.exec.spi;

import java.sql.PreparedStatement;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.hibernate.sql.exec.internal.JdbcMutationExecutorImpl;

/**
 * @author Steve Ebersole
 */
public interface JdbcMutationExecutor {
	/**
	 * Singleton access (calling LogicalConnection#afterStatement afterwards)
	 */
	JdbcMutationExecutor WITH_AFTER_STATEMENT_CALL = new JdbcMutationExecutorImpl( true );

	/**
	 * Singleton access (not calling LogicalConnection#afterStatement afterwards)
	 */
	JdbcMutationExecutor NO_AFTER_STATEMENT_CALL = new JdbcMutationExecutorImpl( false );

	int execute(
			JdbcMutation jdbcMutation,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext,
			Function<String, PreparedStatement> statementCreator,
			BiConsumer<Integer, PreparedStatement> expectationCkeck);

	int execute(
			JdbcMutation jdbcMutation,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext,
			Function<String, PreparedStatement> statementCreator);

	int execute(
			JdbcMutation jdbcMutation,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext,
			BiConsumer<Integer, PreparedStatement> expectationCkeck);

	int execute(
			JdbcMutation jdbcMutation,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext);

}