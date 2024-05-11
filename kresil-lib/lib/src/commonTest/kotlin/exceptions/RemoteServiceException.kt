package exceptions

sealed class RemoteServiceException(message: String) : Exception(message)
