/*
 *
 * Copyright (c) 2019-2020 NasTel Technologies, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of NasTel
 * Technologies, Inc. ("Confidential Information").  You shall not disclose
 * such Confidential Information and shall use it only in accordance with
 * the terms of the license agreement you entered into with NasTel
 * Technologies.
 *
 * NASTEL MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, OR NON-INFRINGEMENT. NASTEL SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING
 * THIS SOFTWARE OR ITS DERIVATIVES.
 *
 * CopyrightVersion 1.0
 */

package com.jkoolcloud.testHarness.harnesses;

public class SQLHarness extends MeasurableHarness {

	@Configurable
	public String url = "jdbc:mysql://172.16.6.75:3306/bank";

	@Configurable
	public Integer port = 3306;

	@Configurable
	public String username = "root";

	@Configurable
	public String password = "password";

	@Configurable
	public String sql = "SELECT 1";

	private Statement statement;

	@Override
	public void setup() throws SQLException, ClassNotFoundException, IllegalAccessException, InstantiationException {
		Class.forName("com.mysql.jdbc.Driver").newInstance();
		Connection connection = DriverManager.getConnection(url, username, password);
		statement = connection.createStatement();

	}

	@Override
	public String call_() throws SQLException {
		ResultSet resultSet = statement.executeQuery(sql);
		String s = String.valueOf(resultSet.getFetchSize());
		resultSet.close();
		return s;
	}
}
