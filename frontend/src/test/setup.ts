import "@testing-library/jest-dom";
import { configure } from "@testing-library/react";

// The suite runs several jsdom workers in parallel, and every vendor assertion
// waits on a query/mutation to settle. Testing Library's 1s default is too tight
// under that load and produced timeouts that pass when a file runs alone.
configure({ asyncUtilTimeout: 5000 });
