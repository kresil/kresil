package kresil.service

/**
 * A remote service that given a specific number of attempts, will succeed after that number of attempts.
 * Internal call counter **is not thread-safe**.
 *
 * As an example, if [succeedAfterAttempt] is **2** then:
 * - the first two calls will throw an exception
 * - the third and subsequent calls will succeed
 *
 * Tries to simulate Java Mockito's [Answer](https://javadoc.io/static/org.mockito/mockito-core/3.2.4/org/mockito/stubbing/Answer.html)
 * behaviour, where one can define an object that will be called on each invocation of a stubbed method,
 * and thus alter the behaviour of the stubbed method on each call.
 *
 * Example:
 * ```java
 *  when(stub.method).then(new Answer<Integer>() {
 *      private int count = 0;
 *      public Integer answer(InvocationOnMock invocation) {
 *          if (count++ < 2) {
 *              throw new WebServiceException("BAM!");
 *          } else {
 *              return 42;
 *          }
 *      }
 * });
 * ```
 * @param succeedAfterAttempt the number of attempts before the service succeeds
 * @param throwable the throwable to throw on each call before the service succeeds
 */
class ConditionalSuccessRemoteService(
    private val succeedAfterAttempt: Int,
    private val throwable: Throwable
) : RemoteService {
    private var callCount = 0 // TODO: not thread-safe

    init {
        require(succeedAfterAttempt > 0) { "succeedAfterAttempt must be greater than 0" }
    }

    override suspend fun suspendSupplier(): String {
        if (++callCount > succeedAfterAttempt) {
            return "Success after $callCount attempts"
        }
        throw throwable
    }

    override suspend fun suspendFunction(input: String): String? {
        TODO("Not yet implemented")
    }

    override suspend fun suspendBiFunction(a: String, b: String): String? {
        TODO("Not yet implemented")
    }
}
