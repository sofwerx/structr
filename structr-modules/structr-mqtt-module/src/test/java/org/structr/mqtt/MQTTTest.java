/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.mqtt;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import static org.hamcrest.CoreMatchers.equalTo;
import org.junit.Test;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.Tx;
import org.structr.mqtt.entity.MQTTClient;
import org.structr.mqtt.entity.MQTTSubscriber;
import org.structr.web.entity.User;

/**
 */
public class MQTTTest extends StructrMQTTModuleTest {

	@Test
	public void testMQTT() {

		final Class subscriberType = StructrApp.getConfiguration().getNodeEntityClass("MQTTSubscriber");
		final Class clientType     = StructrApp.getConfiguration().getNodeEntityClass("MQTTClient");

		try (final Tx tx = app.tx()) {

			app.create(User.class,
				new NodeAttribute<>(StructrApp.key(User.class, "name"),     "admin"),
				new NodeAttribute<>(StructrApp.key(User.class, "password"), "admin"),
				new NodeAttribute<>(StructrApp.key(User.class, "isAdmin"),  true)
			);

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
		}


		try (final Tx tx = app.tx()) {

			final NodeInterface client = app.create(clientType,
				new NodeAttribute<>(StructrApp.key(MQTTClient.class, "url"),       "localhost"),
				new NodeAttribute<>(StructrApp.key(MQTTClient.class, "port"),      12345),
				new NodeAttribute<>(StructrApp.key(MQTTClient.class, "qos"),       2)
			);

			app.create(subscriberType,
				new NodeAttribute<>(StructrApp.key(MQTTSubscriber.class, "client"), client),
				new NodeAttribute<>(StructrApp.key(MQTTSubscriber.class, "topic"),  "test"),
				new NodeAttribute<>(StructrApp.key(MQTTSubscriber.class, "source"), "source")
			);

			tx.success();

		} catch (Throwable t) {
			t.printStackTrace();
		}


		// use RestAssured to check file
		RestAssured
			.given()
			.filter(ResponseLoggingFilter.logResponseTo(System.out))
			.header("X-User", "admin")
			.header("X-Password", "admin")
			.expect()
			.statusCode(200)
			.body("result[0].type",                  equalTo("MQTTClient"))
			.body("result[0].isEnabled",             equalTo(false))
			.body("result[0].isConnected",           equalTo(false))
			.body("result[0].port",                  equalTo(12345))
			.body("result[0].protocol",              equalTo("tcp://"))
			.body("result[0].qos",                   equalTo(2))
			.body("result[0].url",                   equalTo("localhost"))
			.body("result[0].subscribers[0].type",   equalTo("MQTTSubscriber"))
			.body("result[0].subscribers[0].source", equalTo("source"))
			.body("result[0].subscribers[0].topic",  equalTo("test"))
			.when()
			.get("/MQTTClient");
	}

}
