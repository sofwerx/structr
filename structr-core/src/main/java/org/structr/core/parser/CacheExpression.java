/**
 * Copyright (C) 2010-2014 Morgner UG (haftungsbeschränkt)
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.parser;

import org.apache.commons.lang3.StringUtils;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Services;
import org.structr.schema.action.ActionContext;

/**
 *
 * @author Christian Morgner
 */

public class CacheExpression extends Expression {

	private Expression keyExpression     = null;
	private Expression timeoutExpression = null;
	private Expression valueExpression   = null;

	public CacheExpression() {
		super("cache");
	}

	@Override
	public void add(final Expression expression) throws FrameworkException {

		// first expression is the if condition
		if (this.keyExpression == null) {

			this.keyExpression = expression;

		} else if (this.timeoutExpression == null) {

			this.timeoutExpression = expression;

		} else if (this.valueExpression == null) {

			this.valueExpression = expression;

		} else {

			throw new FrameworkException(422, "Invalid cache() expression in builtin function: too many parameters.");
		}

		expression.parent = this;
		expression.level  = this.level + 1;
	}

	@Override
	public Object evaluate(final SecurityContext securityContext, final ActionContext ctx, final GraphObject entity) throws FrameworkException {

		if (keyExpression == null) {
			return "Error: cache(): key expression may not be empty.";
		}

		final String key = keyExpression.evaluate(securityContext, ctx, entity).toString();
		if (StringUtils.isBlank(key)) {
			return "Error: cache(): key may not be empty.";
		}

		if (timeoutExpression == null) {
			return "Error: cache(): timeout expression may not be empty.";
		}

		final Object timeoutValue = timeoutExpression.evaluate(securityContext, ctx, entity);
		if (timeoutValue == null || !(timeoutValue instanceof Number)) {
			return "Error: cache(): timeout must be non-empty and a number.";
		}

		if (valueExpression == null) {
			return "Error: cache(): value expression may not be empty.";
		}

		final long timeout = ((Number)timeoutValue).longValue();

		// get or create new cached value
		final Services services = Services.getInstance();
		CachedValue cachedValue = (CachedValue)services.getAttribute(key);
		if (cachedValue == null) {

			cachedValue = new CachedValue(timeout);
			services.setAttribute(key, cachedValue);

		} else {

			cachedValue.setTimeoutSeconds(timeout);
		}

		// refresh value from value expression (this is the only place the value expression is evaluated)
		if (cachedValue.isExpired()) {
			cachedValue.refresh(valueExpression.evaluate(securityContext, ctx, entity));
		}

		return cachedValue.getValue();
	}

	private static final class CachedValue {

		private Object value        = null;
		private long timeoutSeconds = 0L;
		private long timeout        = 0L;

		public CachedValue(final long timeoutSeconds) {
			this.timeoutSeconds = timeoutSeconds;
		}

		public void setTimeoutSeconds(final long timeoutSeconds) {
			this.timeoutSeconds = timeoutSeconds;
		}

		public final Object getValue() {
			return value;
		}

		public final boolean isExpired() {
			return System.currentTimeMillis() > timeout;
		}

		public final void refresh(final Object value) {

			this.timeout = System.currentTimeMillis() + (timeoutSeconds * 1000);
			this.value   = value;
		}
	}
}
