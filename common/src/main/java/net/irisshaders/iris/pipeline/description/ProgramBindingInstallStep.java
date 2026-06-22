package net.irisshaders.iris.pipeline.description;

public record ProgramBindingInstallStep(
	BindingTarget target,
	BindingInstallerKind installer,
	String directive,
	String requiredContext,
	String conditionalRule
) {
	public String debugName() {
		return target + ":" + installer + ":" + directive + "{context=" + requiredContext + ",condition=" + conditionalRule + "}";
	}
}
