package ch.riesennet.reforge.operations.move

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ImportFixHelperTest {

    @Nested
    inner class InferMapperImplMoves {

        @Test
        fun `mapper interface moved produces Impl entry`() {
            val movedClasses = mapOf(
                "com.old.service.UserMapper" to "com.new.service.UserMapper"
            )
            val mapperFqns = setOf("com.new.service.UserMapper")

            val result = ImportFixHelper.inferMapperImplMoves(movedClasses, mapperFqns)

            assertEquals(
                mapOf("com.old.service.UserMapperImpl" to "com.new.service.UserMapperImpl"),
                result
            )
        }

        @Test
        fun `non-mapper class produces nothing`() {
            val movedClasses = mapOf(
                "com.old.service.TaskService" to "com.new.service.TaskService"
            )
            val mapperFqns = emptySet<String>()

            val result = ImportFixHelper.inferMapperImplMoves(movedClasses, mapperFqns)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `multiple mappers produce entry for each`() {
            val movedClasses = mapOf(
                "com.old.mapper.UserMapper" to "com.new.mapper.UserMapper",
                "com.old.mapper.TaskMapper" to "com.new.mapper.TaskMapper",
                "com.old.service.TaskService" to "com.new.service.TaskService"
            )
            val mapperFqns = setOf("com.new.mapper.UserMapper", "com.new.mapper.TaskMapper")

            val result = ImportFixHelper.inferMapperImplMoves(movedClasses, mapperFqns)

            assertEquals(2, result.size)
            assertEquals("com.new.mapper.UserMapperImpl", result["com.old.mapper.UserMapperImpl"])
            assertEquals("com.new.mapper.TaskMapperImpl", result["com.old.mapper.TaskMapperImpl"])
        }

        @Test
        fun `empty movedClasses returns empty`() {
            val result = ImportFixHelper.inferMapperImplMoves(emptyMap(), setOf("com.new.Mapper"))

            assertTrue(result.isEmpty())
        }

        @Test
        fun `mapper whose Impl is already in movedClasses produces no duplicate`() {
            val movedClasses = mapOf(
                "com.old.mapper.UserMapper" to "com.new.mapper.UserMapper",
                "com.old.mapper.UserMapperImpl" to "com.new.mapper.UserMapperImpl"
            )
            val mapperFqns = setOf("com.new.mapper.UserMapper")

            val result = ImportFixHelper.inferMapperImplMoves(movedClasses, mapperFqns)

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class BuildPackageRenameMap {

        @Test
        fun `single class moved from A to B`() {
            val movedClasses = mapOf(
                "com.old.model.Task" to "com.new.model.Task"
            )

            val result = ImportFixHelper.buildPackageRenameMap(movedClasses)

            assertEquals(mapOf("com.old.model" to setOf("com.new.model")), result)
        }

        @Test
        fun `two classes same old package same new package deduplicates`() {
            val movedClasses = mapOf(
                "com.old.model.Task" to "com.new.model.Task",
                "com.old.model.TaskStatus" to "com.new.model.TaskStatus"
            )

            val result = ImportFixHelper.buildPackageRenameMap(movedClasses)

            assertEquals(mapOf("com.old.model" to setOf("com.new.model")), result)
        }

        @Test
        fun `two classes same old package different new packages`() {
            val movedClasses = mapOf(
                "com.old.model.Task" to "com.new.task.model.Task",
                "com.old.model.Project" to "com.new.project.model.Project"
            )

            val result = ImportFixHelper.buildPackageRenameMap(movedClasses)

            assertEquals(
                mapOf("com.old.model" to setOf("com.new.task.model", "com.new.project.model")),
                result
            )
        }

        @Test
        fun `classes from different old packages produce separate entries`() {
            val movedClasses = mapOf(
                "com.old.model.Task" to "com.new.model.Task",
                "com.old.service.TaskService" to "com.new.service.TaskService"
            )

            val result = ImportFixHelper.buildPackageRenameMap(movedClasses)

            assertEquals(
                mapOf(
                    "com.old.model" to setOf("com.new.model"),
                    "com.old.service" to setOf("com.new.service")
                ),
                result
            )
        }

        @Test
        fun `empty movedClasses returns empty map`() {
            val result = ImportFixHelper.buildPackageRenameMap(emptyMap())

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class ResolveViaPackageRename {

        @Test
        fun `import package matches rename map with unique target`() {
            val packageRenameMap = mapOf(
                "com.old.model" to setOf("com.new.model")
            )

            val result = ImportFixHelper.resolveViaPackageRename(
                "com.old.model.TaskImpl", packageRenameMap
            )

            assertEquals(setOf("com.new.model.TaskImpl"), result)
        }

        @Test
        fun `import package matches rename map with multiple targets`() {
            val packageRenameMap = mapOf(
                "com.old.model" to setOf("com.new.task.model", "com.new.project.model")
            )

            val result = ImportFixHelper.resolveViaPackageRename(
                "com.old.model.TaskImpl", packageRenameMap
            )

            assertEquals(setOf("com.new.task.model.TaskImpl", "com.new.project.model.TaskImpl"), result)
        }

        @Test
        fun `import package does not match any rename returns empty`() {
            val packageRenameMap = mapOf(
                "com.old.model" to setOf("com.new.model")
            )

            val result = ImportFixHelper.resolveViaPackageRename(
                "com.other.service.Foo", packageRenameMap
            )

            assertTrue(result.isEmpty())
        }

        @Test
        fun `import with no package part returns empty`() {
            val packageRenameMap = mapOf(
                "com.old.model" to setOf("com.new.model")
            )

            val result = ImportFixHelper.resolveViaPackageRename(
                "TaskImpl", packageRenameMap
            )

            assertTrue(result.isEmpty())
        }
    }
}
