/*
 * Licensed to STRATIO (C) under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  The STRATIO (C) licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.stratio.cassandra.lucene;

import com.google.common.base.Objects;
import com.stratio.cassandra.lucene.schema.Schema;
import com.stratio.cassandra.lucene.schema.SchemaBuilder;
import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.lucene.analysis.Analyzer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * The Stratio Lucene index user-specified configuration.
 *
 * @author Andres de la Pena {@literal <adelapena@stratio.com>}
 */
public class IndexConfig {

    public static final String SCHEMA_OPTION = "schema";

    public static final String REFRESH_SECONDS_OPTION = "refresh_seconds";
    public static final double DEFAULT_REFRESH_SECONDS = 60;

    public static final String DIRECTORY_PATH_OPTION = "directory_path";
    public static final String INDEXES_DIR_NAME = "lucene";

    public static final String RAM_BUFFER_MB_OPTION = "ram_buffer_mb";
    public static final int DEFAULT_RAM_BUFFER_MB = 64;

    public static final String MAX_MERGE_MB_OPTION = "max_merge_mb";
    public static final int DEFAULT_MAX_MERGE_MB = 5;

    public static final String MAX_CACHED_MB_OPTION = "max_cached_mb";
    public static final int DEFAULT_MAX_CACHED_MB = 30;

    private final ColumnDefinition columnDefinition;
    private final CFMetaData metadata;
    private final Map<String, String> options;
    private final Schema schema;
    private final double refreshSeconds;
    private final Path path;
    private final int ramBufferMB;
    private final int maxMergeMB;
    private final int maxCachedMB;

    /**
     * Builds a new {@link IndexConfig} for the column family defined by the specified metadata using the specified
     * index options.
     *
     * @param metadata         The indexed column family metadata.
     * @param columnDefinition The index column definition.
     */
    public IndexConfig(CFMetaData metadata, ColumnDefinition columnDefinition) {
        this.metadata = metadata;
        this.columnDefinition = columnDefinition;
        options = columnDefinition.getIndexOptions();
        refreshSeconds = parseRefresh();
        ramBufferMB = parseRamBufferMB();
        maxMergeMB = parseMaxMergeMB();
        maxCachedMB = parseMaxCachedMB();
        schema = parseSchema();
        path = parsePath();
    }

    /**
     * Returns the {@link CFMetaData} to be used.
     *
     * @return The {@link CFMetaData} to be used.
     */
    public CFMetaData getMetadata() {
        return metadata;
    }

    /**
     * Returns the {@link ColumnDefinition} to be used.
     *
     * @return The {@link ColumnDefinition} to be used.
     */
    public ColumnDefinition getColumnDefinition() {
        return columnDefinition;
    }

    /**
     * Returns the name of the keyspace to be used.
     *
     * @return The name of the keyspace to be used.
     */
    public String getKeyspaceName() {
        return columnDefinition.ksName;
    }

    /**
     * Returns the name of the table to be used.
     *
     * @return The name of the table to be used.
     */
    public String getTableName() {
        return columnDefinition.cfName;
    }

    /**
     * Returns the name of the index to be used.
     *
     * @return The name of the index to be used.
     */
    public String getIndexName() {
        return columnDefinition.getIndexName();
    }

    /**
     * Returns the full qualified name of the index.
     *
     * @return The full qualified name of the index.
     */
    public String getName() {
        return String.format("%s.%s.%s", getKeyspaceName(), getTableName(), getIndexName());
    }

    /**
     * Returns {@code true} if the index uses wide rows, {@code false} otherwise.
     *
     * @return {@code true} if the index uses wide rows, {@code false} otherwise.
     */
    public boolean isWide() {
        return metadata.clusteringColumns().size() > 0;
    }

    /**
     * Returns the {@link Schema} to be used.
     *
     * @return The {@link Schema} to be used.
     */
    public Schema getSchema() {
        return schema;
    }

    /**
     * Returns the {@link Analyzer} to be used.
     *
     * @return The {@link Analyzer} to be used.
     */
    public Analyzer getAnalyzer() {
        return schema.getAnalyzer();
    }

    /**
     * Returns the path of the directory where the Lucene files will be stored. This directory is collocated to the
     * indexed column family one.
     *
     * @return The path where the Lucene files will be stored.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns the number of seconds before refreshing the index readers.
     *
     * @return The number of seconds before refreshing the index readers.
     */
    public double getRefreshSeconds() {
        return refreshSeconds;
    }

    /**
     * Returns the size of the Lucene index writer write buffer. Its content will be committed to disk when full.
     *
     * @return The size of the write buffer.
     */
    public int getRamBufferMB() {
        return ramBufferMB;
    }

    public int getMaxMergeMB() {
        return maxMergeMB;
    }

    public int getMaxCachedMB() {
        return maxCachedMB;
    }

    private double parseRefresh() {
        String refreshOption = options.get(REFRESH_SECONDS_OPTION);
        double refreshSeconds;
        if (refreshOption != null) {
            try {
                refreshSeconds = Double.parseDouble(refreshOption);
            } catch (NumberFormatException e) {
                throw new IndexException("'%s' must be a strictly positive double", REFRESH_SECONDS_OPTION);
            }
            if (refreshSeconds <= 0) {
                throw new IndexException("'%s' must be strictly positive", REFRESH_SECONDS_OPTION);
            } else {
                return refreshSeconds;
            }
        } else {
            return DEFAULT_REFRESH_SECONDS;
        }
    }

    private int parseRamBufferMB() {
        String ramBufferSizeOption = options.get(RAM_BUFFER_MB_OPTION);
        int ramBufferMB;
        if (ramBufferSizeOption != null) {
            try {
                ramBufferMB = Integer.parseInt(ramBufferSizeOption);
            } catch (NumberFormatException e) {
                throw new IndexException("'%s' must be a strictly positive integer", RAM_BUFFER_MB_OPTION);
            }
            if (ramBufferMB <= 0) {
                throw new IndexException("'%s' must be strictly positive", RAM_BUFFER_MB_OPTION);
            }
            return ramBufferMB;
        } else {
            return DEFAULT_RAM_BUFFER_MB;
        }
    }

    private int parseMaxMergeMB() {
        String maxMergeSizeMBOption = options.get(MAX_MERGE_MB_OPTION);
        int maxMergeMB;
        if (maxMergeSizeMBOption != null) {
            try {
                maxMergeMB = Integer.parseInt(maxMergeSizeMBOption);
            } catch (NumberFormatException e) {
                throw new IndexException("'%s' must be a strictly positive integer", MAX_MERGE_MB_OPTION);
            }
            if (maxMergeMB <= 0) {
                throw new IndexException("'%s' must be strictly positive", MAX_MERGE_MB_OPTION);
            }
            return maxMergeMB;
        } else {
            return DEFAULT_MAX_MERGE_MB;
        }
    }

    private int parseMaxCachedMB() {
        String maxCachedMBOption = options.get(MAX_CACHED_MB_OPTION);
        int maxCachedMB;
        if (maxCachedMBOption != null) {
            try {
                maxCachedMB = Integer.parseInt(maxCachedMBOption);
            } catch (NumberFormatException e) {
                throw new IndexException("'%s' must be a strictly positive integer", MAX_CACHED_MB_OPTION);
            }
            if (maxCachedMB <= 0) {
                throw new IndexException("'%s' must be strictly positive", MAX_CACHED_MB_OPTION);
            }
            return maxCachedMB;
        } else {
            return DEFAULT_MAX_CACHED_MB;
        }
    }

    private Schema parseSchema() {
        String schemaOption = options.get(SCHEMA_OPTION);
        Schema schema;
        if (schemaOption != null && !schemaOption.trim().isEmpty()) {
            try {
                schema = SchemaBuilder.fromJson(schemaOption).build();
                schema.validate(metadata);
            } catch (Exception e) {
                throw new IndexException(e, "'%s' is invalid : %s", SCHEMA_OPTION, e.getMessage());
            }
            return schema;
        } else {
            throw new IndexException("'%s' required", SCHEMA_OPTION);
        }
    }

    private Path parsePath() {
        String pathOption = options.get(DIRECTORY_PATH_OPTION);
        if (pathOption == null) {
            String pathString = DatabaseDescriptor.getAllDataFileLocations()[0] +
                                File.separatorChar +
                                metadata.ksName +
                                File.separatorChar +
                                metadata.cfName +
                                "-" +
                                metadata.cfId +
                                File.separatorChar +
                                INDEXES_DIR_NAME;
            return Paths.get(pathString);
        } else {
            return Paths.get(pathOption);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                      .add("schema", schema)
                      .add("refreshSeconds", refreshSeconds)
                      .add("path", path)
                      .add("ramBufferMB", ramBufferMB)
                      .add("maxMergeMB", maxMergeMB)
                      .add("maxCachedMB", maxCachedMB)
                      .toString();
    }
}
