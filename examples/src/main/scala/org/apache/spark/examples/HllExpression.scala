/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// scalastyle:off println
package org.apache.spark.examples

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.codegen.{CodegenContext, ExprCode}
import org.apache.spark.sql.types.{DataType, StringType, StructField, StructType}

object HllExpression {
  def main(args: Array[String]): Unit = {

    val conf = new SparkConf()
      .setMaster("local[10]")
      .setAppName("UDAF")

    val session = SparkSession
      .builder().config(conf).getOrCreate()

    session.read.json("examples/tongbi.json")
      .createOrReplaceTempView("a")


    session.sql(s" select  CONCAT(SUBSTR(report_date,1,4), 'y'," +
      s" SUBSTR(report_date,5,2), 'm', SUBSTR(report_date,7,2), 'd') " +
      s" AS `report_date_1582684001789`," +
      s"order_num as order_num_1582625795089 " +
      s"from (select compare_sum(report_date,count,'ymd','1') as m from a) t " +
      s"LATERAL VIEW explode(t.m) tt as report_date ,order_num " +
      s"order by tt.report_date desc")
      .show()
  }
}
