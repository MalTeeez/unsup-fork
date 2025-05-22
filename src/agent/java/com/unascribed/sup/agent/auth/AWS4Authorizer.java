package com.unascribed.sup.agent.auth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.unascribed.sup.agent.data.HashFunction;

import okhttp3.Request;
import uk.co.lucasweb.aws.v4.signer.Signer;
import uk.co.lucasweb.aws.v4.signer.Signer.AwsCredentials;

public class AWS4Authorizer implements Authorizer {

	private static final ThreadLocal<SimpleDateFormat> DATE = ThreadLocal.withInitial(() -> {
		var sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		return sdf;
	});
	
	private final String accessId, accessKey, region;

	public AWS4Authorizer(String accessId, String accessKey, String region) {
		this.accessId = accessId;
		this.accessKey = accessKey;
		this.region = region;
	}
	
	@Override
	public void authorize(Request orig, Request.Builder req) {
		var d = DATE.get().format(new Date());
		req.addHeader("Authorization", Signer.builder()
				.awsCredentials(new AwsCredentials(accessId, accessKey))
				.region(region)
				.header("host", orig.url().host())
				.header("x-amz-date", d)
				.build(orig, "s3", HashFunction.SHA2_256.emptyHash())
				.getSignature());
		req.addHeader("x-amz-date", d);
	}
	
}
