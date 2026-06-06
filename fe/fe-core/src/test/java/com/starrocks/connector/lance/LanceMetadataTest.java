// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.starrocks.connector.lance;

import com.google.common.collect.ImmutableList;
import com.starrocks.catalog.Column;
import com.starrocks.catalog.Database;
import com.starrocks.catalog.LanceTable;
import com.starrocks.catalog.Table;
import com.starrocks.type.ArrayType;
import com.starrocks.type.Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static com.starrocks.type.BooleanType.BOOLEAN;
import static com.starrocks.type.FloatType.DOUBLE;
import static com.starrocks.type.FloatType.FLOAT;
import static com.starrocks.type.IntegerType.BIGINT;
import static com.starrocks.type.IntegerType.INT;
import static com.starrocks.type.VarcharType.VARCHAR;

public class LanceMetadataTest {

    @Test
    public void testTypeParsing() {
        // Scalar types
        Assertions.assertEquals(BOOLEAN, LanceApiConverter.parseType("boolean"));
        Assertions.assertEquals(INT, LanceApiConverter.parseType("int32"));
        Assertions.assertEquals(BIGINT, LanceApiConverter.parseType("int64"));
        Assertions.assertEquals(FLOAT, LanceApiConverter.parseType("float32"));
        Assertions.assertEquals(DOUBLE, LanceApiConverter.parseType("float64"));
        Assertions.assertEquals(VARCHAR, LanceApiConverter.parseType("string"));

        // Nested types
        Type arrayType = LanceApiConverter.parseType("list<float32>");
        Assertions.assertTrue(arrayType.isArrayType());
        Assertions.assertEquals(FLOAT, ((ArrayType) arrayType).getItemType());

        // Vector embeddings as fixed_size_list
        Type vectorType = LanceApiConverter.parseType("fixed_size_list<float32, 128>");
        Assertions.assertTrue(vectorType.isArrayType());
        Assertions.assertEquals(FLOAT, ((ArrayType) vectorType).getItemType());

        // Struct types
        Type structType = LanceApiConverter.parseType("struct<id:int64,embedding:fixed_size_list<float32,512>>");
        Assertions.assertTrue(structType.isStructType());
    }

    @Test
    public void testMetadataDiscovery() {
        LanceMetadata metadata = new LanceMetadata("lance_catalog", new HashMap<>());
        Assertions.assertEquals(Table.TableType.LANCE, metadata.getTableType());

        Database db = new Database(10001, "db1");
        metadata.addDatabase(db);

        List<Column> columns = ImmutableList.of(
                new Column("id", INT),
                new Column("embedding", LanceApiConverter.parseType("fixed_size_list<float32, 128>"))
        );
        LanceTable table = new LanceTable(20001, "vectors_table", columns, "s3://bucket/vectors");
        metadata.addTable("db1", table);

        // Assert discovery API
        Assertions.assertTrue(metadata.listDbNames(null).contains("db1"));
        Assertions.assertTrue(metadata.listTableNames(null, "db1").contains("vectors_table"));
        Assertions.assertEquals(db, metadata.getDb(null, "db1"));

        Table discovered = metadata.getTable(null, "db1", "vectors_table");
        Assertions.assertNotNull(discovered);
        Assertions.assertTrue(discovered.isLanceTable());
        LanceTable lanceDiscovered = (LanceTable) discovered;
        Assertions.assertEquals("s3://bucket/vectors", lanceDiscovered.getUri());
        Assertions.assertEquals(2, lanceDiscovered.getColumns().size());
        Column embeddingCol = lanceDiscovered.getColumn("embedding");
        Assertions.assertNotNull(embeddingCol);
        Assertions.assertTrue(embeddingCol.getType().isArrayType());
    }
}
