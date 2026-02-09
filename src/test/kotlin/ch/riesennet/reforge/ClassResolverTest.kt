package ch.riesennet.reforge

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClassResolverTest {

    @Test
    fun `exact match with no wildcards`() {
        assertTrue(ClassResolver.matches("com.example.Foo", "com.example.Foo"))
    }

    @Test
    fun `exact match fails for different class`() {
        assertFalse(ClassResolver.matches("com.example.Foo", "com.example.Bar"))
    }

    @Test
    fun `single star matches within segment`() {
        assertTrue(ClassResolver.matches("com.example.Task*", "com.example.TaskService"))
        assertTrue(ClassResolver.matches("com.example.Task*", "com.example.Task"))
        assertTrue(ClassResolver.matches("com.example.Task*", "com.example.TaskController"))
    }

    @Test
    fun `single star does not cross package boundary`() {
        assertFalse(ClassResolver.matches("com.example.*", "com.example.sub.Foo"))
    }

    @Test
    fun `single star at end of segment`() {
        assertTrue(ClassResolver.matches("com.example.*", "com.example.Foo"))
        assertTrue(ClassResolver.matches("com.example.*", "com.example.Bar"))
    }

    @Test
    fun `double star matches zero or more package segments`() {
        assertTrue(ClassResolver.matches("com.example.**.Foo", "com.example.Foo"))
        assertTrue(ClassResolver.matches("com.example.**.Foo", "com.example.sub.Foo"))
        assertTrue(ClassResolver.matches("com.example.**.Foo", "com.example.sub.deep.Foo"))
    }

    @Test
    fun `double star at end matches everything`() {
        assertTrue(ClassResolver.matches("com.example.**", "com.example.Foo"))
        assertTrue(ClassResolver.matches("com.example.**", "com.example.sub.Foo"))
    }

    @Test
    fun `mixed wildcards`() {
        assertTrue(ClassResolver.matches("com.example.**.*Service", "com.example.task.TaskService"))
        assertTrue(ClassResolver.matches("com.example.**.*Service", "com.example.deep.sub.MyService"))
        assertFalse(ClassResolver.matches("com.example.**.*Service", "com.example.task.TaskController"))
    }

    @Test
    fun `dots are escaped in regex`() {
        // A dot in the pattern should not match arbitrary characters
        assertFalse(ClassResolver.matches("com.example.Foo", "comXexample.Foo"))
    }

    @Test
    fun `patternToRegex produces anchored regex`() {
        val regex = ClassResolver.patternToRegex("com.example.Foo")
        // Should not match substring
        assertFalse(regex.matches("prefix.com.example.Foo"))
        assertFalse(regex.matches("com.example.Foo.suffix"))
    }

    @Test
    fun `star in middle of name`() {
        assertTrue(ClassResolver.matches("com.example.*Task*", "com.example.MyTaskService"))
        assertTrue(ClassResolver.matches("com.example.*Task*", "com.example.TaskService"))
        assertFalse(ClassResolver.matches("com.example.*Task*", "com.example.sub.MyTaskService"))
    }
}
