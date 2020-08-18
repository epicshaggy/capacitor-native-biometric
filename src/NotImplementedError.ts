export class NotImplementedError extends Error {
  constructor(public message: string) {
    super(message);
    this.name = "Not implemented error";
  }
}
