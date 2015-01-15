package com.revin.net;

/**
 * http://en.wikipedia.org/wiki/List_of_HTTP_status_codes
 * Created by revintec on 14-8-19.
 */
public class HttpStatusCodes{
    @Deprecated
    public static final int
    HTTP305_UseProxy=305,
    HTTP418_ImaTeapot=418,
    HTTP420_MethodFailure=420,
    HTTP420_EnhanceYourClam=420;

    public static final int
    HTTP100_Continue=100,
    HTTP101_SwitchingProtocols=101,
    HTTP102_Processing=102,

    HTTP200_OK=200,
    HTTP201_Created=201,
    HTTP202_Accepted=202,
    HTTP203_NonAuthoritativeInformation=203,
    HTTP204_NoContent=204,
    HTTP205_ResetContent=205,
    HTTP206_PartialContent=206,
    HTTP207_MultiStatus=207,
    HTTP208_AlreadyReported=208,
    HTTP226_IMUsed=226,

    HTTP300_MultipleChoices=300,
    /** reissue the request with GET */
    HTTP301_MovedPermanently=301,
    /** reissue the request with GET */
    HTTP302_Found=302,
    /** request processed, get the result with GET */
    HTTP303_SeeOther=303,
    HTTP304_NotModified=304,
    HTTP306_SwitchProxy=306,
    HTTP307_TemporaryRedirect=307,
    HTTP308_PermanentRedirect=308,

    HTTP400_BadRequest=400,
    HTTP401_Unauthorized=401,
    HTTP402_PaymentRequired=402,
    HTTP403_Forbidden=403,
    HTTP404_NotFound=404,
    HTTP405_MethodNotAllowed=405,
    HTTP406_NotAcceptable=406,
    HTTP407_ProxyAuthenticationRequired=407,
    HTTP408_RequestTimeout=408,
    HTTP409_Conflict=409,
    HTTP410_Gone=410,
    HTTP411_LengthRequired=411,
    HTTP412_PreconditionFailed=412,
    HTTP413_RequestEntityTooLarge=413,
    HTTP414_RequestURITooLong=414,
    HTTP415_UnsupportedMediaType=415,
    HTTP416_RequestedRangeNotSatisfiable=416,
    HTTP417_ExpectationFailed=417,
    HTTP419_AuthenticationTimeout=419,
    HTTP422_UnprocessableEntity=422,
    HTTP423_Locked=423,
    HTTP424_FailedDependency=424,
    HTTP426_UpgradeRequired=426,
    HTTP428_PreconditionRequired=428,
    HTTP429_TooManyRequests=429,
    HTTP431_RequestHeaderFieldsTooLarge=431,
    HTTP440_LoginTimeout=440,
    HTTP444_NoResponse=444,

    HTTP500_InternalServerError=500,
    HTTP501_NotImplemented=501,
    HTTP502_BadGateway=502,
    HTTP503_ServiceUnavailable=503,
    HTTP504_GatewayTimeout=504,
    HTTP505_HttpVersionNotSupported=505,
    HTTP507_InsufficientStorage=507,
    HTTP508_LoopDetected=508,
    HTTP509_BandwidthLimitExceeded=509,
    HTTP510_NotExtended=510,
    HTTP511_NetworkAuthenticationRequired=511;
}
